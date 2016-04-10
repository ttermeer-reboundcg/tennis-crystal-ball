package org.strangeforest.tcb.stats.web;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import javax.annotation.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;
import org.strangeforest.tcb.stats.util.*;

import com.google.common.cache.*;
import com.maxmind.geoip2.record.*;

import static java.util.stream.Collectors.*;

@Service
public class VisitorManager {

	@Autowired private VisitorRepository repository;
	@Autowired private GeoIPService geoIPService;

	@Value("${tennis-stats.visitors.expiry-period:PT1H}")
	private Duration expiryPeriod = Duration.ofHours(1);

	@Value("${tennis-stats.visitors.expiry-check-period:PT5M}")
	private Duration expiryCheckPeriod = Duration.ofMinutes(5);

	@Value("${tennis-stats.visitors.save-every-visit-count:10}")
	private int saveEveryVisitCount = 10;

	@Value("${tennis-stats.visitors.cache-size:1000}")
	private int cacheSize = 1000;

	private LoadingCache<String, Optional<Visitor>> visitors;
	private ScheduledExecutorService visitorExpirer;
	private ScheduledFuture<?> visitorExpirerFuture;

	@PostConstruct
	public void init() {
		visitors = CacheBuilder.newBuilder().maximumSize(cacheSize).build(
			new CacheLoader<String, Optional<Visitor>>() {
				public Optional<Visitor> load(String ipAddress) {
					return repository.find(ipAddress);
				}
			}
		);
		visitors.putAll(repository.findAll().stream().collect(toMap(Visitor::getIpAddress, Optional::of)));
		visitorExpirer = Executors.newSingleThreadScheduledExecutor();
		long period = expiryCheckPeriod.getSeconds();
		visitorExpirerFuture = visitorExpirer.scheduleAtFixedRate(this::expire, period, period, TimeUnit.SECONDS);
	}

	@PreDestroy
	public void destroy() throws InterruptedException {
		if (visitors == null)
			return;
		try {
			try {
				if (visitorExpirer != null) {
					if (visitorExpirerFuture != null) {
						visitorExpirerFuture.cancel(false);
						visitorExpirerFuture = null;
					}
					visitorExpirer.shutdown();
					visitorExpirer.awaitTermination(15L, TimeUnit.SECONDS);
					visitorExpirer = null;
				}
			}
			finally {
				expire();
				repository.saveAll(visitorStream().filter(Visitor::isDirty).collect(toList()));
			}
		}
		finally {
			visitors.cleanUp();
			visitors = null;
		}
	}

	public Visitor visit(String ipAddress) {
		try {
			Optional<Visitor> optionalVisitor = visitors.get(ipAddress);
			if (!optionalVisitor.isPresent()) {
				Optional<Country> country = geoIPService.getCountry(ipAddress);
				String countryId = country.isPresent() ? country.get().getIsoCode() : null;
				Visitor visitor = repository.create(ipAddress, countryId);
				optionalVisitor = Optional.of(visitor);
				visitors.put(ipAddress, optionalVisitor);
				return visitor;
			}
			else {
				Visitor visitor = optionalVisitor.get();
				int visits = visitor.visit();
				if (saveEveryVisitCount > 0 && visits % saveEveryVisitCount == 0) {
					repository.save(visitor);
					visitor.clearDirty();
				}
				else
					visitor.setDirty();
				return visitor;
			}
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			throw new TennisStatsException("Error tracking visit.", cause != null ? cause : null);
		}
	}

	private void expire() {
		visitorStream().filter(visitor -> visitor.isExpired(expiryPeriod)).forEach(visitor -> {
			repository.expire(visitor);
			visitors.invalidate(visitor.getIpAddress());
		});
	}

	private Stream<Visitor> visitorStream() {
		return visitors.asMap().values().stream().map(Optional::get);
	}
}
