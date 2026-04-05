package tools;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * ONE-TIME DATA FETCHER — run once to generate places.json.
 *
 * Usage:  mvn exec:java -Dexec.mainClass="tools.FetchPlacesData"
 *
 * Queries Overpass for all cities, towns, and airports worldwide.
 * Saves to src/main/resources/places.json.
 * Re-run once a year to refresh data.
 */
public final class FetchPlacesData {

    private static final String OVERPASS = "https://overpass-api.de/api/interpreter";
    private static final String OUT_PATH = "src/main/resources/places.json";

    // Cities and towns with population >= 100,000 + all airports with IATA codes
    private static final String QUERY =
        "[out:json][timeout:180];\n" +
        "(\n" +
        "  node[\"place\"=\"city\"];\n" +
        "  node[\"place\"=\"town\"][\"population\"](if: number(t[\"population\"]) >= 100000);\n" +
        "  node[\"aeroway\"=\"aerodrome\"][\"iata\"];\n" +
        "  way[\"aeroway\"=\"aerodrome\"][\"iata\"];\n" +
        ");\n" +
        "out center tags;";

    public static void main(String[] args) throws Exception {
        System.out.println("Querying Overpass... (30-120 seconds)");
        String json = post(QUERY);
        if (json == null) { System.err.println("Failed."); System.exit(1); }

        System.out.println("Parsing...");
        StringBuilder sb = new StringBuilder("{\"places\":[");
        boolean first = true;
        int[] counts = {0, 0}; // cities, airports

        // Parse elements array
        int depth = 0, start = -1;
        int elIdx = json.indexOf("\"elements\"");
        if (elIdx < 0) { System.err.println("No elements in response."); System.exit(1); }
        int arrStart = json.indexOf('[', elIdx);

        for (int i = arrStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}' && --depth == 0 && start >= 0) {
                String el = json.substring(start, i + 1);
                start = -1;

                // Coordinates
                String lat = field(el, "lat"), lon = field(el, "lon");
                if (lat == null || lon == null) {
                    int ci = el.indexOf("\"center\"");
                    if (ci >= 0) { String c2 = el.substring(ci); lat = field(c2,"lat"); lon = field(c2,"lon"); }
                }
                if (lat == null || lon == null) continue;

                int ti = el.indexOf("\"tags\"");
                if (ti < 0) continue;
                String tags = el.substring(ti);

                String place   = field(tags, "place");
                String aeroway = field(tags, "aeroway");
                boolean isAirport = "aerodrome".equals(aeroway);
                boolean isPlace   = "city".equals(place) || "town".equals(place);
                if (!isAirport && !isPlace) continue;

                String name = field(tags, "name:en");
                if (name == null) name = field(tags, "name");
                if (name == null) continue;
                name = name.replace("\\","\\\\").replace("\"","\\\"");

                String popS = field(tags, "population");
                double pop = 0;
                if (popS != null) try { pop = Double.parseDouble(popS.replaceAll("[^0-9]","")); } catch(Exception ignored){}

                double imp;
                String type;
                if (isAirport) {
                    imp = 0.6; type = "city"; // airports shown as city pins but with airport=true
                } else {
                    imp = pop > 0 ? Math.min(0.95, 0.3 + Math.log10(pop+1)/7.5)
                                  : "city".equals(place) ? 0.6 : 0.4;
                    type = "city".equals(place) ? "city" : "town";
                }

                if (!first) sb.append(',');
                sb.append(String.format(
                    "{\"name\":\"%s\",\"lon\":%s,\"lat\":%s,\"imp\":%.4f,\"type\":\"%s\",\"airport\":%b}",
                    name, lon, lat, imp, type, isAirport));
                first = false;
                if (isAirport) counts[1]++; else counts[0]++;
            }
        }
        sb.append("]}");

        Path out = Paths.get(OUT_PATH);
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        System.out.printf("Done: %d cities/towns, %d airports → %s%n",
            counts[0], counts[1], out.toAbsolutePath());
    }

    private static String post(String query) {
        try {
            String body = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpURLConnection c = (HttpURLConnection) URI.create(OVERPASS).toURL().openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("User-Agent", "TSP-Solver/1.0 (one-time data fetch)");
            c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
            c.setConnectTimeout(30_000); c.setReadTimeout(300_000); c.setDoOutput(true);
            try(OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
            if (c.getResponseCode() != 200) { System.err.println("HTTP "+c.getResponseCode()); return null; }
            StringBuilder sb = new StringBuilder();
            try(BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line; while((line=br.readLine())!=null) sb.append(line);
            }
            return sb.toString();
        } catch(Exception e) { System.err.println(e.getMessage()); return null; }
    }

    private static String field(String obj, String key) {
        String s = "\""+key+"\""; int ki = obj.indexOf(s); if(ki<0) return null;
        int col = obj.indexOf(':', ki+s.length()); if(col<0) return null;
        int vi = col+1; while(vi<obj.length()&&obj.charAt(vi)==' ') vi++;
        if(vi>=obj.length()) return null;
        if(obj.charAt(vi)=='"') {
            int end=vi+1; while(end<obj.length()){if(obj.charAt(end)=='"'&&obj.charAt(end-1)!='\\') break; end++;} return obj.substring(vi+1,end);
        } else {
            int end=vi; while(end<obj.length()){char c=obj.charAt(end); if(c==','||c=='}'||c==']'||c==' '||c=='\n') break; end++;}
            String v=obj.substring(vi,end).trim(); return v.isEmpty()?null:v;
        }
    }
}
