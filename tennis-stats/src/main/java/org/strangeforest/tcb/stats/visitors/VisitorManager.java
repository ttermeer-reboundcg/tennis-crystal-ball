package org.strangeforest.tcb.stats.visitors;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import javax.annotation.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;
import org.strangeforest.tcb.stats.util.*;
import org.strangeforest.tcb.util.*;

import com.github.benmanes.caffeine.cache.*;
import com.maxmind.geoip2.record.Country;
import com.neovisionaries.i18n.*;
import io.micrometer.core.instrument.util.*;

import static java.util.stream.Collectors.*;

@Service @VisitorSupport
public class VisitorManager {

	@Autowired private VisitorRepository repository;
	@Autowired private GeoIPService geoIPService;

	@Value("${tennis-stats.visitors.expiry-period:PT1H}")
	private Duration expiryPeriod = Duration.ofHours(1);

	@Value("${tennis-stats.visitors.expiry-check-period:PT5M}")
	private Duration expiryCheckPeriod = Duration.ofMinutes(5);

	@Value("${tennis-stats.visitors.save-every-hit-count:10}")
	private int saveEveryHitCount = 10;

	@Value("${tennis-stats.visitors.cache-size:1000}")
	private int cacheSize = 1000;

	private LockManager<String> lockManager;
	private LoadingCache<String, Optional<Visitor>> visitors;
	private ScheduledExecutorService visitorExpirer;
	private ScheduledFuture<?> visitorExpirerFuture;

	@PostConstruct
	public void init() {
		lockManager = new LockManager<>();
		visitors = Caffeine.newBuilder()
			.maximumSize(cacheSize)
			.removalListener(this::visitorRemoved)
			.build(repository::find);
		visitors.putAll(repository.findAll().stream().collect(toMap(Visitor::getIpAddress, Optional::of)));
		visitorExpirer = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Visitor Expirer"));
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
				repository.saveAll(cachedVisitorStream().filter(Visitor::isDirty).collect(toList()));
				expire();
			}
		}
		finally {
			visitors.cleanUp();
			visitors = null;
		}
	}

	public Visitor visit(String ipAddress, String agentType) {
		try {
			return doVisit(ipAddress, agentType);
		}
		catch (ExecutionException ex) {
			throw new TennisStatsException("Error tracking visit.", Optional.ofNullable(ex.getCause()).orElse(ex));
		}
		catch (Exception ex) {
			throw new TennisStatsException("Error tracking visit.", ex);
		}
	}

	private Visitor doVisit(String ipAddress, String agentType) throws Exception {
		return lockManager.withLock(ipAddress, () -> {
			Optional<Visitor> optionalVisitor = visitors.get(ipAddress);
			if (optionalVisitor.isEmpty()) {
				Optional<Country> optionalCountry = geoIPService.getCountry(ipAddress);
				String countryId = null;
				String countryName = null;
				if (optionalCountry.isPresent()) {
					Country country = optionalCountry.get();
					CountryCode code = CountryCode.getByCode(country.getIsoCode());
					if (code != null)
						countryId = code.getAlpha3();
					countryName = country.getName();
				}
				Visitor visitor = repository.create(ipAddress, countryId, countryName, agentType);
				visitors.put(ipAddress, Optional.of(visitor));
				return visitor;
			}
			else {
				Visitor visitor = optionalVisitor.get();
				int hits = visitor.visit();
				if (saveEveryHitCount > 0 && (hits % saveEveryHitCount == 0)) {
					repository.save(visitor);
					visitor.clearDirty();
				}
				else
					visitor.setDirty();
				return visitor;
			}
		});
	}

	private void expire() {
		visitorStream().filter(visitor -> visitor.isExpired(expiryPeriod)).forEach(visitor -> {
			repository.expire(visitor);
			visitors.invalidate(visitor.getIpAddress());
		});
	}

	private Stream<Visitor> visitorStream() {
		return repository.findAll().stream();
	}

	private Stream<Visitor> cachedVisitorStream() {
		return visitors.asMap().values().stream().map(Optional::get);
	}

	void clearCache() {
		if (visitors != null)
			visitors.invalidateAll();
	}

	private void visitorRemoved(String ipAddress, Optional<Visitor> optionalVisitor, RemovalCause cause) {
		if (cause.wasEvicted()) {
			if (optionalVisitor != null && optionalVisitor.isPresent())
				repository.save(optionalVisitor.get());
		}
	}
}
