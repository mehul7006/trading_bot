public class UpstoxConfig {
    public static final String API_KEY = "768a303b-80f1-46d6-af16-f847f9341213";
    public static final String API_SECRET = "j0w9ga2m9w";
    public static final String ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OWU4NDkyMDdjNDE5ZDFjNWQwMGIxYjYiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc3NjgzMDc1MiwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzc2ODk1MjAwfQ.ZrzQH8sCDVXxeCaeTKSvqVEZIf8YzMrZAsW9Qy5GrPc";
    public static final String BASE_URL = "https://api.upstox.com/v2";
    
    public static String getAuthHeader() {
        return "Bearer " + ACCESS_TOKEN;
    }
}
