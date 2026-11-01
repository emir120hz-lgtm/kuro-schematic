package kuro;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Istege bagli Discord webhook bildirimi. Varsayilan KAPALI (Config.dnWebhook).
 * Adres kuro_webhook.txt icindeki ilk satirdan okunur; sadece Donut bildirim metni gonderilir
 * (koordinat dahil), baska hicbir veri (hesap, IP, sunucu adi) gonderilmez.
 */
public final class Webhook {
   private static final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "kuro-webhook");
      t.setDaemon(true);
      return t;
   });
   private static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

   private Webhook() {
   }

   private static Path file() {
      return FabricLoader.getInstance().getGameDir().resolve("kuro_webhook.txt");
   }

   private static String url() {
      Path f = file();
      try {
         if (!Files.exists(f)) {
            Files.writeString(f, "# Discord webhook adresini bu satirin altina yaz (https://discord.com/api/webhooks/...)\n");
            return null;
         }

         for (String l : Files.readAllLines(f)) {
            l = l.trim();
            if (l.startsWith("https://discord.com/api/webhooks/") || l.startsWith("https://discordapp.com/api/webhooks/")) {
               return l;
            }
         }
      } catch (IOException ignored) {
      }

      return null;
   }

   public static void send(String text) {
      pool.submit(() -> {
         String u = url();
         if (u == null) {
            return;
         }

         String json = "{\"content\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"}";
         try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(u))
               .timeout(Duration.ofSeconds(8))
               .header("Content-Type", "application/json")
               .POST(HttpRequest.BodyPublishers.ofString(json))
               .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
            Thread.sleep(1200L); // basit hiz siniri
         } catch (IOException | InterruptedException | RuntimeException ignored) {
         }
      });
   }
}
