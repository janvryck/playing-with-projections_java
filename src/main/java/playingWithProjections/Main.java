package playingWithProjections;

public class Main {
    public static void main(String[] args) {
        String file = FilePathFrom(args);
        CountEvents projector = new CountEvents();

        CountRegisteredPlayers projector2 = new CountRegisteredPlayers();

        new EventStore(projector::projection, projector2::projection)
            .replay(file);

        System.out.printf("number of events: %d%n", projector.getResult());
        System.out.printf("number of registered players: %d%n", projector2.getResult());
    }

    private static String FilePathFrom(String[] args) {
        if (args.length < 1) throw new IllegalArgumentException("Please specify a file to replay");
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

    private static class CountRegisteredPlayers{
        private int counter = 0;
        void projection(Event event) {
            if(event.getType().equals("PlayerHasRegistered")){
                ++counter;
            }
        }

        int getResult() {
            return counter;
        }
    }
}
