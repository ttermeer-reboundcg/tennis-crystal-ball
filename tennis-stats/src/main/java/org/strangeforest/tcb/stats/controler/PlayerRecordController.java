package org.strangeforest.tcb.stats.controler;

import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.*;
import org.strangeforest.tcb.stats.model.*;

@Controller
public class PlayerRecordController {

	@Autowired private JdbcTemplate jdbcTemplate;

	private static final String PLAYER_QUERY =
		"SELECT player_id, name, dob, extract(year from age) AS age, country_id, birthplace, residence, height, weight, " +
				"hand, backhand, turned_pro, coach, " +
				"titles, grand_slams, tour_finals, masters, olympics, " +
				"current_rank, current_rank_points, best_rank, best_rank_date, best_rank_points, best_rank_points_date, goat_rank, goat_points, " +
				"web_site, twitter, facebook " +
		"FROM player_v";

		private static final String PLAYER_BY_NAME = PLAYER_QUERY + " WHERE name = ? ORDER BY goat_points DESC NULLS LAST, best_rank DESC NULLS LAST LIMIT 1";
		private static final String PLAYER_BY_ID = PLAYER_QUERY + " WHERE player_id = ?";

	@RequestMapping("/playerRecord")
	public ModelAndView playerRecord(
		@RequestParam(value = "id", required = false) Integer id,
		@RequestParam(value = "name", required = false) String name
	) {
		Player playerModel = id != null || name != null ? jdbcTemplate.queryForObject(id != null ? PLAYER_BY_ID : PLAYER_BY_NAME, (rs, rowNum) -> {

			Player player = new Player(rs.getInt("player_id"));
			player.setName(rs.getString("name"));
			player.setDob(rs.getDate("dob"));
			player.setAge(rs.getInt("age"));
			player.setCountryId(rs.getString("country_id"));
			player.setBirthplace(rs.getString("birthplace"));
			player.setResidence(rs.getString("residence"));
			player.setHeight(rs.getInt("height"));
			player.setWeight(rs.getInt("weight"));

			player.setHand(rs.getString("hand"));
			player.setBackhand(rs.getString("backhand"));
			player.setTurnedPro(rs.getInt("turned_pro"));
			player.setCoach(rs.getString("coach"));

			player.setTitles(rs.getInt("titles"));
			player.setGrandSlams(rs.getInt("grand_slams"));
			player.setTourFinals(rs.getInt("tour_finals"));
			player.setMasters(rs.getInt("masters"));
			player.setOlympics(rs.getInt("olympics"));

			player.setCurrentRank(rs.getInt("current_rank"));
			player.setCurrentRankPoints(rs.getInt("current_rank_points"));
			player.setBestRank(rs.getInt("best_rank"));
			player.setBestRankDate(rs.getDate("best_rank_date"));
			player.setBestRankPoints(rs.getInt("best_rank_points"));
			player.setBestRankPointsDate(rs.getDate("best_rank_points_date"));
			player.setGoatRank(rs.getInt("goat_rank"));
			player.setGoatRankPoints(rs.getInt("goat_points"));

			player.setWebSite(rs.getString("web_site"));
			player.setTwitter(rs.getString("twitter"));
			player.setFacebook(rs.getString("facebook"));

			return player;
		}, id != null ? id : name) : null;
		return new ModelAndView("playerRecord", "player", playerModel);
	}
}