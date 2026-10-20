package com.voxelport.mod.logic;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * The official relay regions. A public port belongs to the region that handed it out, so the host connects to
 * one region and players use that region's public host. The desktop app, the Android app and the CLI follow the
 * same rule: the lowest measured latency wins, India if nothing can be measured.
 */
public final class Regions {
    public record Region(String id, String label, List<String> relayUrls, List<String> statusUrls, String publicHost) {}

    public static final Region INDIA = new Region(
            "in", "India",
            List.of(RelayUrlResolver.DIRECT_RELAY_URL, RelayUrlResolver.DEFAULT_RELAY_URL),
            List.of("https://direct.voxelport.in:26499/api/status", "https://relay.voxelport.in/api/status"),
            "play.voxelport.in");
    public static final Region EUROPE = new Region(
            "eu", "Europe",
            List.of("wss://eu.voxelport.in/ws"),
            List.of("https://eu.voxelport.in/api/status"),
            "play-eu.voxelport.in");
    public static final List<Region> ALL = List.of(INDIA, EUROPE);

    /** Returned by a measurement that could not reach the region. */
    public static final long UNREACHABLE = Long.MAX_VALUE;

    private Regions() {}

    /**
     * The region to use. An explicit preference ("in" or "eu") wins; anything else is "auto", which takes the
     * lowest latency and falls back to India when neither answers.
     */
    public static Region pick(String preference, ToLongFunction<Region> measure) {
        if (preference != null) {
            for (Region r : ALL) if (r.id().equalsIgnoreCase(preference.trim())) return r;
        }
        long[] times = new long[ALL.size()];
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < ALL.size(); i++) {
            final int idx = i;
            Thread t = Thread.ofVirtual().start(() -> times[idx] = measure.applyAsLong(ALL.get(idx)));
            threads.add(t);
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return INDIA;
            }
        }
        Region best = INDIA;
        long bestTime = UNREACHABLE;
        for (int i = 0; i < ALL.size(); i++) {
            if (times[i] < bestTime) {
                best = ALL.get(i);
                bestTime = times[i];
            }
        }
        return best;
    }

    /** Best round trip (ms) of a few small requests to the region's status endpoint, UNREACHABLE if it doesn't answer. */
    public static long measure(Region region) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        long best = UNREACHABLE;
        for (String url : region.statusUrls()) {
            for (int i = 0; i < 3; i++) {
                long start = System.nanoTime();
                try {
                    HttpResponse<Void> resp = client.send(
                            HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build(),
                            HttpResponse.BodyHandlers.discarding());
                    if (resp.statusCode() == 200) best = Math.min(best, (System.nanoTime() - start) / 1_000_000L);
                } catch (IOException e) {
                    break; // not reachable this way: don't wait for more timeouts
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return UNREACHABLE;
                }
            }
            if (best != UNREACHABLE) break; // the preferred route works; skip the fallback
        }
        return best;
    }
}
