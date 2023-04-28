package playingWithProjections;

import static java.util.Collections.emptyMap;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        String file = FilePathFrom(args);

        CountEvents projector = new CountEvents();
        CountRegisteredPlayers projector2 = new CountRegisteredPlayers();
        CountRegisteredPlayersByMonth proj3 = new CountRegisteredPlayersByMonth();

        new EventStore(projector::projection, projector2::projection, proj3::projection)
            .replay(file);

        System.out.printf("number of events: %d%n", projector.getResult());
        System.out.printf("number of registered players: %d%n", projector2.getResult());
        System.out.printf("number of registered players by month:\n%s", proj3.getResult());
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
                return year + "-" + month ;
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
}
