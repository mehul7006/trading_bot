public class UpstoxConfig {
    public static final String API_KEY = "768a303b-80f1-46d6-af16-f847f9341213";
    public static final String API_SECRET = "j0w9ga2m9w";
    public static final String ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OWI1MWI4MzUxOTQzMzYxYTEwMjhjN2UiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc3MzQ3NjczOSwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzczNTI1NjAwfQ.b_wse2HpUAAhecUsu50-eT-3CFUdX-AdMOyHEuHOA8w";
    public static final String BASE_URL = "https://api.upstox.com/v2";
    
    public static String getAuthHeader() {
        return "Bearer " + ACCESS_TOKEN;
    }
}
