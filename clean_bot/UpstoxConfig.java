public class UpstoxConfig {
    public static final String API_KEY = "768a303b-80f1-46d6-af16-f847f9341213";
    public static final String API_SECRET = "j0w9ga2m9w";
    public static final String ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTE3MzVjZmI1MTBhZDM2MDNhMTJkNjciLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2MzEyODc4MywiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzYzMTU3NjAwfQ.E40et7KwwJ9htWG_ppgtoYQMdmdtLopNuiU_wmBPnqA";
    public static final String BASE_URL = "https://api.upstox.com/v2";
    
    public static String getAuthHeader() {
        return "Bearer " + ACCESS_TOKEN;
    }
}
