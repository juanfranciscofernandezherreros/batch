package com.example.csvbatchlauncher.pronostico;

import io.github.aigen.article.application.ArticleGeneratorService;
import io.github.aigen.article.domain.Article;
import io.github.aigen.article.domain.ArticleRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PronosticoService {
    private static final String COMPETITION = "euroleague";
    private static final String COUNTRY = "europe";
    private static final String COLLECTION = "prediction_articles";
    private static final String MODEL = "db-net-form-logistic-v1+ollama-article";
    private static final double HOME_ADVANTAGE = 2.5;
    private static final double LOGISTIC_SCALE = 9.0;
    private static final Pattern TAG_STRIP = Pattern.compile("[^A-Z0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> TEAM_CODE_NAMES = Map.ofEntries(
            Map.entry("BAR", "Barcelona"), Map.entry("BAS", "Baskonia"),
            Map.entry("BAY", "Bayern"), Map.entry("BES", "Besiktas"),
            Map.entry("CRV", "Crvena Zvezda Meridianbet"), Map.entry("DUB", "Dubai"),
            Map.entry("EFE", "Anadolu Efes"), Map.entry("FEN", "Fenerbahce"),
            Map.entry("HAP", "Hapoel Tel Aviv"), Map.entry("LYO", "Lyon-Villeurbanne"),
            Map.entry("MAC", "Maccabi Tel Aviv"), Map.entry("OLI", "Olimpia Milano"),
            Map.entry("OLY", "Olympiacos"), Map.entry("PAR", "Paris"),
            Map.entry("PAT", "Panathinaikos"), Map.entry("PTZ", "Partizan Mozzart Bet"),
            Map.entry("REA", "Real Madrid"), Map.entry("VAL", "Valencia"),
            Map.entry("VIR", "Virtus Bologna"), Map.entry("ZAL", "Zalgiris Kaunas"));

    private final JdbcTemplate jdbc;
    private final MongoTemplate mongo;
    private final ArticleGeneratorService articleGenerator;
    private final String site;

    public PronosticoService(JdbcTemplate jdbc, MongoTemplate mongo,
            ArticleGeneratorService articleGenerator,
            @Value("${article-generator.site:http://localhost:8083}") String site) {
        this.jdbc = jdbc;
        this.mongo = mongo;
        this.articleGenerator = articleGenerator;
        this.site = site;
    }

    public PublishedPrediction publishNextRound() {
        LocalDate firstDate = jdbc.queryForObject("""
                SELECT min(event_datetime::date)
                FROM fixtures.fixtures
                WHERE country=? AND competition=? AND event_datetime::date >= CURRENT_DATE
                """, LocalDate.class, COUNTRY, COMPETITION);
        if (firstDate == null) throw new IllegalStateException("No hay fixtures futuros de EuroLeague en PostgreSQL");

        List<Fixture> fixtures = jdbc.query("""
                SELECT match_id, home_team, away_team, event_datetime
                FROM fixtures.fixtures
                WHERE country=? AND competition=?
                  AND event_datetime::date BETWEEN ? AND ?
                ORDER BY event_datetime, match_id
                """, (rs, row) -> new Fixture(rs.getString("match_id"), rs.getString("home_team"),
                rs.getString("away_team"), rs.getObject("event_datetime", OffsetDateTime.class)),
                COUNTRY, COMPETITION, firstDate, firstDate.plusDays(3));
        if (fixtures.isEmpty()) throw new IllegalStateException("No se encontraron partidos en la próxima jornada");

        List<TeamMetrics> metrics = loadTeamMetrics(firstDate);
        Map<String, TeamMetrics> byTeam = new HashMap<>();
        metrics.forEach(metric -> byTeam.put(normalize(metric.team()), metric));
        List<PlayerLeader> leaders = loadPlayerLeaders(firstDate);
        Map<String, List<PlayerLeader>> playersByTeam = new HashMap<>();
        leaders.forEach(player -> playersByTeam.computeIfAbsent(normalize(player.team()), ignored -> new ArrayList<>()).add(player));
        Map<String, Map<String, Double>> teamStats = loadTeamStats(firstDate);
        DataCoverage coverage = loadCoverage(firstDate);
        int resultCount = resultCount(firstDate);
        int leagueTeams = (int) fixtures.stream().flatMap(f -> java.util.stream.Stream.of(f.homeTeam(), f.awayTeam()))
                .map(PronosticoService::normalize).distinct().count();
        int gamesPerRound = Math.max(1, leagueTeams / 2);
        String round = resultCount > 0 && resultCount % gamesPerRound == 0
                ? "Jornada " + (resultCount / gamesPerRound + 1)
                : "Próxima jornada";
        LocalDate lastDate = fixtures.get(fixtures.size() - 1).startsAt().toLocalDate();
        String articleId = "euroliga-" + slug(round) + "-" + firstDate;
        String title = "Pronóstico EuroLeague — " + round + " (" + firstDate + "–" + lastDate + ")";

        List<Document> games = new ArrayList<>();
        StringBuilder promptData = new StringBuilder();
        for (Fixture fixture : fixtures) {
            TeamMetrics home = byTeam.get(normalize(fixture.homeTeam()));
            TeamMetrics away = byTeam.get(normalize(fixture.awayTeam()));
            if (home == null || away == null) throw new IllegalStateException("Faltan resultados previos para " + fixture.homeTeam() + " / " + fixture.awayTeam());
            double expectedMargin = 0.65 * (home.seasonNet() - away.seasonNet())
                    + 0.35 * (home.recentNet() - away.recentNet()) + HOME_ADVANTAGE;
            double homeProbability = 1.0 / (1.0 + Math.exp(-expectedMargin / LOGISTIC_SCALE));
            double homePct = round1(homeProbability * 100.0);
            double awayPct = round1(100.0 - homePct);
            String winner = homePct >= awayPct ? fixture.homeTeam() : fixture.awayTeam();
            int winnerPct = (int) Math.round(Math.max(homePct, awayPct));
            List<PlayerLeader> homePlayers = playersByTeam.getOrDefault(normalize(fixture.homeTeam()), List.of()).stream().limit(3).toList();
            List<PlayerLeader> awayPlayers = playersByTeam.getOrDefault(normalize(fixture.awayTeam()), List.of()).stream().limit(3).toList();
            List<PlayerLeader> matchPlayers = playersByTeam.getOrDefault(normalize(winner), List.of()).stream().limit(3).toList();
            Map<String, Double> statValues = teamStats.getOrDefault(normalize(fixture.homeTeam()), Map.of());
            Map<String, Double> awayStatValues = teamStats.getOrDefault(normalize(fixture.awayTeam()), Map.of());
            String analysis = analysis(home, away, homePct, awayPct, winner, matchPlayers, statValues, awayStatValues);
            Document game = new Document("matchId", fixture.matchId())
                    .append("homeTeam", fixture.homeTeam()).append("awayTeam", fixture.awayTeam())
                    .append("startsAt", fixture.startsAt().toInstant().toString())
                    .append("prediction", String.format(Locale.ROOT, "Quiniela %s · 1: %.1f%% · X: 0.0%% · 2: %.1f%%", homePct >= awayPct ? "1" : "2", homePct, awayPct))
                    .append("winner", winner).append("winnerProbability", winnerPct)
                    .append("homeProbability", homePct).append("awayProbability", awayPct)
                    .append("expectedMargin", round1(expectedMargin)).append("analysis", analysis)
                    .append("homeStats", new Document("played", home.played()).append("wins", home.wins()).append("netPerGame", home.seasonNet()).append("lastFiveNet", home.recentNet()).append("sample", home.recentGames()).append("lastFiveFourthQuarterNet", home.recentFourthQuarterNet()).append("lastFiveCloseGames", home.recentCloseGames()).append("boxScore", statValues))
                    .append("awayStats", new Document("played", away.played()).append("wins", away.wins()).append("netPerGame", away.seasonNet()).append("lastFiveNet", away.recentNet()).append("sample", away.recentGames()).append("lastFiveFourthQuarterNet", away.recentFourthQuarterNet()).append("lastFiveCloseGames", away.recentCloseGames()).append("boxScore", awayStatValues))
                    .append("homePlayerLeaders", playerDocuments(homePlayers)).append("awayPlayerLeaders", playerDocuments(awayPlayers))
                    .append("playerLeaders", playerDocuments(matchPlayers));
            games.add(game);
            promptData.append(String.format(Locale.ROOT,
                    "%s vs %s: %d/%d victorias; neto temporada %.1f/%.1f; neto últimos %d %.1f/%.1f; probabilidad calculada %.1f/%.1f. Contexto team_stats Other casa=%s fuera=%s. Líderes de anotación asociados al equipo elegido=%s.%n",
                    fixture.homeTeam(), fixture.awayTeam(), home.wins(), home.played(), home.seasonNet(), away.seasonNet(),
                    Math.min(home.recentGames(), away.recentGames()), home.recentNet(), away.recentNet(), homePct,
                    awayPct, statValues, awayStatValues, matchPlayers));
            promptData.append("Jugadores locales=").append(homePlayers).append("; jugadores visitantes=")
                    .append(awayPlayers).append("; diferencial Q4 local/visitante=")
                    .append(home.recentFourthQuarterNet()).append("/").append(away.recentFourthQuarterNet())
                    .append("; partidos recientes decididos por cinco o menos=")
                    .append(home.recentCloseGames()).append("/").append(away.recentCloseGames()).append(". ");
        }

        String methodology = "Pronóstico calculado por el módulo con datos disponibles en PostgreSQL hasta " + firstDate.minusDays(1)
                + ". Fórmula transparente: margen esperado = 0,65 × diferencia del diferencial medio de temporada + 0,35 × diferencia del diferencial medio de los últimos cinco partidos + 2,5 puntos de localía; probabilidad local = logística(margen/9). El visitante es el complemento y X=0% porque la Euroliga resuelve con prórroga. No hay cuotas ni lesiones en las tablas consultadas. Las probabilidades son heurísticas, con muestras cortas y sin calibración histórica; no son garantías.";
        methodology += " Control de datos: match_summary está disponible para " + coverage.matchSummaryMatches()
                + " partidos; hay parciales completos en " + coverage.completeQuarterScores() + "/" + resultCount
                + ". PBP cubre " + coverage.pointByPointMatches() + " partidos, pero el último evento coincide con el resultado solo en "
                + coverage.pointByPointScoreAgreement() + "; por eso no modifica las probabilidades. Las estadísticas team_stats de acierto de tiro se excluyen por inconsistencias; se muestran solo métricas de la categoría Other. Los promedios de jugadores son descriptivos y requieren al menos tres apariciones.";
        String aiContext = "Jornada=" + round + "; fechas=" + firstDate + " a " + lastDate + "; resultados previos=" + resultCount
                + "; cobertura=" + coverage + "; partidos=" + promptData;
        Article article = articleGenerator.generateArticle(ArticleRequest.builder()
                .category("Pronósticos de EuroLeague").subcategory(round)
                .tag(aiContext).title(title).language("es").authorUsername("Basketball Forecast")
                .site(site).build());

        String generatedAt = Instant.now().toString();
        Document document = new Document("_id", articleId).append("id", articleId)
                .append("competition", "EuroLeague").append("round", round)
                .append("title", article.getTitle()).append("introduction", article.getSummary() == null || article.getSummary().isBlank()
                        ? "Analisis de EuroLeague basado en resultados, rendimiento reciente y estadisticas locales."
                        : article.getSummary())
                .append("articleBody", article.getBody() == null ? "" : article.getBody()).append("seo", new Document("slug", article.getSlug())
                        .append("keywords", article.getKeywords()).append("metaTitle", article.getMetaTitle())
                        .append("metaDescription", article.getMetaDescription()))
                .append("games", games).append("methodology", methodology)
                .append("dataCoverage", new Document("results", resultCount).append("matchSummary", coverage.matchSummaryMatches())
                        .append("pointByPoint", coverage.pointByPointMatches()).append("pointByPointFinalScoreAgreement", coverage.pointByPointScoreAgreement())
                        .append("completeQuarterScores", coverage.completeQuarterScores())
                        .append("completeQuarterScores", coverage.completeQuarterScores()).append("teamStatsMatches", coverage.teamStatsMatches())
                        .append("playerStatsMatches", coverage.playerStatsMatches()))
                .append("sources", List.of(new Document("title", "PostgreSQL local: fixtures.fixtures, results.results, team_stats.team_stats, stats_player.stats_player, match_summary.match_summary y point_by_point.point_by_point_event")
                        .append("url", "http://localhost:55432/basketball")))
                .append("generatedAt", generatedAt).append("startsAt", fixtures.get(0).startsAt().toInstant().toString())
                .append("model", MODEL).append("localMatches", resultCount);
        mongo.getCollection(COLLECTION).replaceOne(new Document("_id", articleId), document,
                new com.mongodb.client.model.ReplaceOptions().upsert(true));
        return new PublishedPrediction(articleId, round, games.size(), site + "/predictions/" + articleId, MODEL);
    }

    private List<TeamMetrics> loadTeamMetrics(LocalDate before) {
        return jdbc.query("""
                WITH team_games AS (
                  SELECT home_team AS team, home_score AS points_for, away_score AS points_against,
                         (home_score > away_score)::int AS win, event_datetime,
                         CASE WHEN home_score4 IS NOT NULL AND away_score4 IS NOT NULL THEN home_score4-away_score4 END AS q4_net
                  FROM results.results WHERE country=? AND competition=? AND event_datetime::date < ?
                  UNION ALL
                  SELECT away_team, away_score, home_score, (away_score > home_score)::int, event_datetime,
                         CASE WHEN home_score4 IS NOT NULL AND away_score4 IS NOT NULL THEN away_score4-home_score4 END
                  FROM results.results WHERE country=? AND competition=? AND event_datetime::date < ?
                ), ranked AS (
                  SELECT *, row_number() OVER (PARTITION BY team ORDER BY event_datetime DESC) AS recency
                  FROM team_games
                )
                SELECT team, count(*) AS played, sum(win) AS wins,
                       avg(points_for-points_against) AS season_net,
                       avg(points_for-points_against) FILTER (WHERE recency <= 5) AS recent_net,
                       count(*) FILTER (WHERE recency <= 5) AS recent_games,
                       count(*) FILTER (WHERE recency <= 5 AND abs(points_for-points_against) <= 5) AS recent_close_games,
                       avg(q4_net) FILTER (WHERE recency <= 5) AS recent_q4_net
                FROM ranked GROUP BY team
                """, (rs, row) -> new TeamMetrics(rs.getString("team"), rs.getInt("played"), rs.getInt("wins"),
                round1(rs.getDouble("season_net")), round1(rs.getDouble("recent_net")), rs.getInt("recent_games"),
                rs.getInt("recent_close_games"), round1(rs.getDouble("recent_q4_net"))),
                COUNTRY, COMPETITION, before, COUNTRY, COMPETITION, before);
    }

    private List<PlayerLeader> loadPlayerLeaders(LocalDate before) {
        List<PlayerLeader> all = jdbc.query("""
                SELECT p.team, p.name, avg(p.pts) AS points, avg(p.reb) AS rebounds,
                       avg(p.ast) AS assists, count(DISTINCT p.match_id) AS games
                FROM stats_player.stats_player p
                JOIN results.results r ON r.match_id=p.match_id
                WHERE r.country=? AND r.competition=? AND r.event_datetime::date < ?
                GROUP BY p.team, p.name
                HAVING count(DISTINCT p.match_id) >= 3
                ORDER BY p.team, avg(p.pts) DESC
                """, (rs, row) -> new PlayerLeader(TEAM_CODE_NAMES.getOrDefault(rs.getString("team").toUpperCase(Locale.ROOT), rs.getString("team")),
                rs.getString("name"), round1(rs.getDouble("points")), round1(rs.getDouble("rebounds")),
                round1(rs.getDouble("assists")), rs.getInt("games")), COUNTRY, COMPETITION, before);
        return all.stream().filter(p -> TEAM_CODE_NAMES.containsValue(p.team())).toList();
    }

    private Map<String, Map<String, Double>> loadTeamStats(LocalDate before) {
        Map<String, Map<String, Double>> result = new HashMap<>();
        jdbc.query("""
                WITH sides AS (
                  SELECT r.home_team AS team, t.metric,
                         NULLIF(regexp_replace(t.home_value, '[^0-9.-]', '', 'g'), '')::numeric AS value
                  FROM team_stats.team_stats t JOIN results.results r ON r.match_id=t.match_id
                  WHERE r.country=? AND r.competition=? AND r.event_datetime::date < ?
                    AND t.category='Other' AND t.home_team=r.home_team
                  UNION ALL
                  SELECT r.away_team, t.metric,
                         NULLIF(regexp_replace(t.away_value, '[^0-9.-]', '', 'g'), '')::numeric
                  FROM team_stats.team_stats t JOIN results.results r ON r.match_id=t.match_id
                  WHERE r.country=? AND r.competition=? AND r.event_datetime::date < ?
                    AND t.category='Other' AND t.away_team=r.away_team
                )
                SELECT team, metric, avg(value) AS average FROM sides
                WHERE metric IN ('Assists','Turnovers','Total rebounds','Steals','Blocks') AND value IS NOT NULL
                GROUP BY team, metric
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    result.computeIfAbsent(normalize(rs.getString("team")), ignored -> new HashMap<>())
                            .put(rs.getString("metric"), round1(rs.getDouble("average")));
                },
                COUNTRY, COMPETITION, before, COUNTRY, COMPETITION, before);
        return result;
    }

    private DataCoverage loadCoverage(LocalDate before) {
        return jdbc.queryForObject("""
                WITH season_results AS (
                  SELECT * FROM results.results WHERE country=? AND competition=? AND event_datetime::date < ?
                ), last_events AS (
                  SELECT DISTINCT ON (e.match_id) e.match_id, e.home_score, e.away_score
                  FROM point_by_point.point_by_point_event e JOIN season_results r ON r.match_id=e.match_id
                  ORDER BY e.match_id, e.sequence DESC
                )
                SELECT (SELECT count(DISTINCT ms.match_id) FROM match_summary.match_summary ms JOIN season_results r USING(match_id)) AS summaries,
                  (SELECT count(DISTINCT e.match_id) FROM point_by_point.point_by_point_event e JOIN season_results r USING(match_id)) AS pbp,
                  (SELECT count(DISTINCT r.match_id) FROM season_results r JOIN last_events e USING(match_id)
                    WHERE e.home_score=r.home_score AND e.away_score=r.away_score) AS pbp_score_agreement,
                  (SELECT count(*) FROM season_results WHERE home_score1 IS NOT NULL AND home_score2 IS NOT NULL
                    AND home_score3 IS NOT NULL AND home_score4 IS NOT NULL AND away_score1 IS NOT NULL
                    AND away_score2 IS NOT NULL AND away_score3 IS NOT NULL AND away_score4 IS NOT NULL) AS complete_quarters,
                  (SELECT count(DISTINCT t.match_id) FROM team_stats.team_stats t JOIN season_results r USING(match_id)) AS team_stats,
                  (SELECT count(DISTINCT p.match_id) FROM stats_player.stats_player p JOIN season_results r USING(match_id)) AS player_stats
                """, (rs, row) -> new DataCoverage(rs.getInt("summaries"), rs.getInt("pbp"),
                rs.getInt("pbp_score_agreement"), rs.getInt("complete_quarters"),
                rs.getInt("team_stats"), rs.getInt("player_stats")), COUNTRY, COMPETITION, before);
    }

    private int resultCount(LocalDate before) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM results.results WHERE country=? AND competition=? AND event_datetime::date < ?",
                Integer.class, COUNTRY, COMPETITION, before);
        return count == null ? 0 : count;
    }

    private static String analysis(TeamMetrics home, TeamMetrics away, double homePct, double awayPct,
            String pick, List<PlayerLeader> players, Map<String, Double> homeBox, Map<String, Double> awayBox) {
        String edge = homePct >= awayPct ? "local" : "visitante";
        String playerContext = players.isEmpty() ? "Sin tres apariciones de jugador suficientes para mostrar líderes." :
                "Líderes de puntos del equipo elegido en la base: " + players.stream()
                        .map(p -> p.name() + " (" + p.points() + " pts, " + p.games() + " partidos)").toList() + ".";
        return String.format(Locale.ROOT,
                "La estimación favorece al %s por una ventaja prevista de %.1f%% frente a %.1f%%. " +
                "Balance y diferencial medio: local %d/%d, %.1f; visitante %d/%d, %.1f. " +
                "En los últimos %d encuentros el diferencial medio fue %.1f y %.1f. " +
                "Team stats fiables disponibles (rebotes, asistencias, robos, tapones y pérdidas): %s / %s. %s",
                edge, Math.max(homePct, awayPct), Math.min(homePct, awayPct), home.wins(), home.played(), home.seasonNet(),
                away.wins(), away.played(), away.seasonNet(), Math.min(home.recentGames(), away.recentGames()),
                home.recentNet(), away.recentNet(), homeBox, awayBox, playerContext);
    }

    private static String normalize(String value) {
        return TAG_STRIP.matcher(value == null ? "" : value).replaceAll("").toUpperCase(Locale.ROOT);
    }

    private static List<Document> playerDocuments(List<PlayerLeader> players) {
        return players.stream().map(p -> new Document("name", p.name()).append("team", p.team())
                .append("pointsPerGame", p.points()).append("reboundsPerGame", p.rebounds())
                .append("assistsPerGame", p.assists()).append("games", p.games())).toList();
    }

    private static String slug(String value) {
        return TAG_STRIP.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("^-|-$", "");
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record Fixture(String matchId, String homeTeam, String awayTeam, OffsetDateTime startsAt) {}
    private record TeamMetrics(String team, int played, int wins, double seasonNet, double recentNet,
            int recentGames, int recentCloseGames, double recentFourthQuarterNet) {}
    private record PlayerLeader(String team, String name, double points, double rebounds, double assists, int games) {}
    private record DataCoverage(int matchSummaryMatches, int pointByPointMatches, int pointByPointScoreAgreement,
            int completeQuarterScores, int teamStatsMatches, int playerStatsMatches) {}
}
