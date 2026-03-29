public class UpstoxConfig {
    public static final String API_KEY = "3954b352-747f-4d01-91d2-78365c79cc95";
    public static final String API_SECRET = "mdefgew8sv";
    public static final String ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OWM5Mjc2OGFiZTEwNzY4YTMwMzlhMGEiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc3NDc5MDUwNCwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzc0ODIxNjAwfQ.-TEeYkh5dIrsymF46Wugc9Cjvw2JeWMcI5qjhaB3_dg";
    public static final String BASE_URL = "https://api.upstox.com/v2";
    
    public static String getAuthHeader() {
        return "Bearer " + ACCESS_TOKEN;
    }
}
