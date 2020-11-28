public class StoreMessage {
    private final String url;
    private final int time;

    public StoreMessage(String url, int time) {
        this.url = url;
        this.time = time;
    }

    public String getUrl() {
        return url;
    }

    public int getTime() {
        return time;
    }
}
