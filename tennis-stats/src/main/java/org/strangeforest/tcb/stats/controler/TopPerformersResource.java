package org.strangeforest.tcb.stats.controler;

import java.util.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.strangeforest.tcb.stats.model.*;
import org.strangeforest.tcb.stats.model.table.*;
import org.strangeforest.tcb.stats.service.*;
import org.strangeforest.tcb.stats.util.*;

import static org.strangeforest.tcb.stats.util.OrderBy.*;

@RestController
public class TopPerformersResource {

	@Autowired private TopPerformersService topPerformersService;

	private static Map<String, String> ORDER_MAP = new TreeMap<>();
	static {
		ORDER_MAP.put("wonLostPct", "won_lost_pct");
		ORDER_MAP.put("won", "won");
		ORDER_MAP.put("lost", "lost");
	}
	private static final OrderBy[] DEFAULT_ORDERS = new OrderBy[] {desc("won"), asc("lost"), asc("name")};

	@RequestMapping("/topPerformersTable")
	public BootgridTable<TopPerformerRow> topPerformersTable(
		@RequestParam(value = "category") String category,
		@RequestParam(value = "season", required = false) Integer season,
		@RequestParam(value = "current") int current,
		@RequestParam(value = "rowCount") int rowCount,
		@RequestParam(value = "searchPhrase") String searchPhrase,
		@RequestParam Map<String, String> requestParams
	) {
		StatsPlayerListFilter filter = new StatsPlayerListFilter(searchPhrase, season);
		int playerCount = topPerformersService.getPlayerCount(category, filter);

		String orderBy = BootgridUtil.getOrderBy(requestParams, ORDER_MAP, DEFAULT_ORDERS);
		int pageSize = rowCount > 0 ? rowCount : playerCount;
		return topPerformersService.getTopPerformersTable(category, playerCount, filter, orderBy, pageSize, current);
	}

	@RequestMapping("/topPerformersMinEntries")
	public String topPerformersMinEntries(
		@RequestParam(value = "category") String category,
		@RequestParam(value = "season", required = false) Integer season
	) {
		StatsPlayerListFilter filter = new StatsPlayerListFilter(season);
		return topPerformersService.getTopPerformersMinEntries(category, filter);
	}
}
