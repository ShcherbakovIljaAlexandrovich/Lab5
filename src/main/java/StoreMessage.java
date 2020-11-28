public class StoreMessage {
    private final String url;
    private final long time;

    public StoreMessage(String url, long time) {
        this.url = url;
        this.time = time;
    }

    public String getUrl() {
        return url;
    }

    public long getTime() {
        return time;
    }
}
