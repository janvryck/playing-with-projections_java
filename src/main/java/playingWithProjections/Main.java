package playingWithProjections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        String file = FilePathFrom(args);

        CountEvents projector = new CountEvents();
        CountRegisteredPlayers projector2 = new CountRegisteredPlayers();
        CountRegisteredPlayersByMonth proj3 = new CountRegisteredPlayersByMonth();
        CountMostPopularQuizzes prj5 = new CountMostPopularQuizzes();
        CountFakeNews pr6 = new CountFakeNews();
        // CountMostPopularQuizzesByPlayers p1 = new CountMostPopularQuizzesByPlayers();

        new EventStore(projector::projection, projector2::projection, proj3::projection, prj5::projection, pr6::projection/*, p1::projection*/)
            .replay(file);

        System.out.printf("=> number of events: %d%n", projector.getResult());
        System.out.printf("=> number of registered players: %d%n", projector2.getResult());
        System.out.printf("=> number of registered players by month:\n%s\n", proj3.getResult());
        System.out.printf("=> most popular quizzes by #games:\n%s\n", prj5.getResult());
        System.out.printf("=> does opening player have to join game as well: %s\n", pr6.getResult());
        // System.out.printf("=> most popular quizzes by #players:\n%s\n", p1.getResult());
    }

    private static String FilePathFrom(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Please specify a file to replay");
        }
        return args[0];
    }

    private static class CountEvents {
        private int counter = 0;

        int getResult() {
            return counter;
        }

        void projection(Event event) {
            counter++;
        }
    }

    private static class CountRegisteredPlayers {
        private int counter = 0;

        void projection(Event event) {
            if (event.getType().equals("PlayerHasRegistered")) {
                ++counter;
            }
        }

        int getResult() {
            return counter;
        }
    }

    private static class CountRegisteredPlayersByMonth {

        private class MonthYear {
            private final int year;
            private final int month;

            private MonthYear(int year, int month) {
                this.year = year;
                this.month = month;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }

                MonthYear monthYear = (MonthYear) o;

                if (year != monthYear.year) {
                    return false;
                }
                return month == monthYear.month;
            }

            @Override
            public int hashCode() {
                int result = year;
                result = 31 * result + month;
                return result;
            }

            @Override
            public String toString() {
                return year + "-" + month;
            }
        }

        private Map<MonthYear, Integer> countByMonth = new HashMap<>();

        void projection(Event event) {
            if (event.getType().equals("PlayerHasRegistered")) {
                countByMonth.compute(
                    new MonthYear(event.getTimestamp().getYear(), event.getTimestamp().getMonth().getValue()),
                    (monthYear, count) -> count == null ? 1 : count + 1
                );
            }
        }

        String getResult() {
            return countByMonth.toString();
        }
    }

    private static class CountMostPopularQuizzes {
        Map<String, String> quizNameById = new HashMap<>();
        Map<String, Integer> gamesByQuizId = new HashMap<>();

        String getResult() {
            return gamesByQuizId.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(entry -> quizNameById.get(entry.getKey()) + ": " + entry.getKey() + " (" + entry.getValue() + ")")
                .collect(Collectors.joining("\n"));
        }

        void projection(Event event) {
            if (event.getType().equals("QuizWasCreated")) {
                quizNameById.put(event.getPayload().get("quiz_id"), event.getPayload().get("quiz_title"));
            }
            if (event.getType().equals("GameWasOpened")) {
                gamesByQuizId.compute(
                    event.getPayload().get("quiz_id"),
                    (quizId, count) -> count == null ? 1 : count + 1
                );
            } else if (event.getType().equals("GameWasCancelled")) {
                gamesByQuizId.compute(
                    event.getPayload().get("quiz_id"),
                    (quizId, count) -> count == null ? -1 : count - 1
                );
            }
        }
    }

    private static class CountFakeNews {
        Set<String> uniques = new HashSet<>();

        boolean fakeNews = false;

        String getResult() {
            return fakeNews + "";
        }

        void projection(Event event) {
            if (event.getType().equals("GamesOpened")) {
                uniques.add(event.getPayload().get("game_id") + ":" + event.getPayload().get("player_id"));
            }
            if (event.getType().equals("¨PlayerJoinedGame")) {
                if (uniques.contains(event.getPayload().get("game_id") + ":" + event.getPayload().get("player_id"))) {
                    fakeNews = true;
                }
            }
        }
    }

    private static class CountMostPopularQuizzesByPlayers {
        Map<String, String> quizNameById = new HashMap<>();
        Map<String, List<String>> gamesByQuizId = new HashMap<>();
        Map<String, Integer> playerCountByGameId = new HashMap<>();

        String getResult() {
            return gamesByQuizId.entrySet().stream()
                .map(entry -> Map.entry(
                    entry.getKey(),
                    entry.getValue().stream()
                        .mapToInt(gameId -> {
                            Integer playercount = playerCountByGameId.get(gameId);
                            return playercount == null ? 0 : playercount;
                        })
                        .sum()
                ))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(entry -> quizNameById.get(entry.getKey()) + ": " + entry.getKey() + " (" + entry.getValue() + ")")
                .collect(Collectors.joining("\n"));
        }

        void projection(Event event) {
            if (event.getType().equals("QuizWasCreated")) {
                quizNameById.put(event.getPayload().get("quiz_id"), event.getPayload().get("quiz_title"));
            } else if (event.getType().equals("GameWasOpened")) {
                gamesByQuizId.compute(
                    event.getPayload().get("quiz_id"),
                    (quizId, games) -> games == null ? new ArrayList<String>() {{
                        add(event.getPayload().get("game_id"));
                    }} : new ArrayList<String>(games) {{
                        add(event.getPayload().get("game_id"));
                    }}
                );
                playerCountByGameId.putIfAbsent(event.getPayload().get("game_id"), 1);
            } else if (event.getType().equals("GameWasCancelled")) {
                gamesByQuizId.compute(
                    event.getPayload().get("quiz_id"),
                    (quizId, count) -> new ArrayList<String>() {{
                        remove(event.getPayload().get("game_id"));
                    }}
                );
            } else if (event.getType().equals("PlayerJoinedGame")) {
                playerCountByGameId.compute(event.getPayload().get("game_id"), (gameId, count) -> count + 1);
            }
        }
    }
}
