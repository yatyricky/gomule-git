package gomule.d2i;

import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Quick standalone diagnostic to figure out the chronicle timestamp encoding.
 */
public class TimestampDiagnostic {
    public static void main(String[] args) {
        // Raw 10-byte entries from the _good.d2i file for entries with known game times
        // Format: name, raw hex bytes, game-displayed time (local to user)
        Object[][] data = {
            {"Andariel's Visage *345", new int[]{0xAF,0x57,0x5F,0xAC,0xC2,0x01,0x59,0x01,0x00,0x00}, "03/16/2026 21:30"},
            {"Coif of Glory *8",       new int[]{0x4E,0x0C,0xE5,0xCB,0xC2,0x01,0x08,0x00,0x00,0x00}, "03/03/2026 13:59"},
            {"Harlequin Crest *308",   new int[]{0x09,0x0C,0xF6,0xC2,0xC2,0x01,0x34,0x01,0x00,0x00}, "02/26/2026 23:48"},
            {"Duskdeep *9",            new int[]{0x9F,0x0B,0x63,0xBD,0xC2,0x01,0x09,0x00,0x00,0x00}, "03/01/2026 00:14"},
            {"Veil of Steel *316",     new int[]{0x43,0x0C,0xC2,0xD3,0xC2,0x01,0x3C,0x01,0x00,0x00}, "03/08/2026 21:20"},
        };

        DateTimeFormatter gameFmt = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
        Instant epoch = Instant.EPOCH;

        for (Object[] row : data) {
            String name = (String) row[0];
            int[] b = (int[]) row[1];
            String gameTime = (String) row[2];

            // Parse game time as UTC (for baseline comparison)
            LocalDateTime gameLdt = LocalDateTime.parse(gameTime, gameFmt);
            long gameSecUtc = gameLdt.toEpochSecond(ZoneOffset.UTC);
            long gameMinUtc = gameSecUtc / 60;

            // bytes[2..5] as u32 LE
            long u32 = (b[2] & 0xFFL) | ((b[3] & 0xFFL) << 8) | ((b[4] & 0xFFL) << 16) | ((b[5] & 0xFFL) << 24);

            long diffSec = gameSecUtc - u32 * 60;
            double diffHours = diffSec / 3600.0;

            Instant decoded = Instant.ofEpochSecond(u32 * 60);
            String decodedStr = decoded.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"));

            System.out.printf("%s%n", name);
            System.out.printf("  Game (local?): %s%n", gameTime);
            System.out.printf("  Binary (UTC):  %s  [u32=%d]%n", decodedStr, u32);
            System.out.printf("  Diff: %d sec = %.2f hours%n", diffSec, diffHours);

            // Now try: what if game shows local time in various TZs?
            for (String tzId : new String[]{"America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
                                             "Europe/London", "Europe/Berlin", "Europe/Istanbul", "Asia/Kolkata",
                                             "Asia/Tokyo", "Asia/Seoul", "Asia/Shanghai", "Australia/Sydney"}) {
                ZoneId tz = ZoneId.of(tzId);
                ZonedDateTime gameZdt = gameLdt.atZone(tz);
                long gameSecTz = gameZdt.toEpochSecond();
                long diffSecTz = gameSecTz - u32 * 60;
                if (Math.abs(diffSecTz) < 120) { // within 2 minutes
                    System.out.printf("  *** MATCH in %s: diff=%d sec ***%n", tzId, diffSecTz);
                }
            }

            // Also try interpreting u32 as seconds (not minutes)
            // decoded would be in 1970...
            // What if bytes[0..5] is a 48-bit value?
            long u48 = (b[0] & 0xFFL) | ((b[1] & 0xFFL) << 8) | ((b[2] & 0xFFL) << 16)
                     | ((b[3] & 0xFFL) << 24) | ((b[4] & 0xFFL) << 32) | ((b[5] & 0xFFL) << 40);
            // As seconds
            if (u48 > 1700000000L && u48 < 1900000000L) {
                Instant inst = Instant.ofEpochSecond(u48);
                String s = inst.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"));
                long diff48 = gameSecUtc - u48;
                System.out.printf("  u48 as seconds: %s (diff=%d sec = %.2f hours)%n", s, diff48, diff48 / 3600.0);
            }

            System.out.println();
        }

        // Let me also cross-check by computing what bytes[2..5] SHOULD be 
        // for each timezone and game time
        System.out.println("=== REVERSE: What u32 value would produce each game time? ===");
        for (Object[] row : data) {
            String name = (String) row[0];
            int[] b = (int[]) row[1];
            String gameTime = (String) row[2];
            long u32 = (b[2] & 0xFFL) | ((b[3] & 0xFFL) << 8) | ((b[4] & 0xFFL) << 16) | ((b[5] & 0xFFL) << 24);

            LocalDateTime gameLdt = LocalDateTime.parse(gameTime, gameFmt);
            System.out.printf("%s: actual u32=%d (0x%08X)%n", name, u32, u32);
            for (String tzId : new String[]{"UTC", "America/New_York", "America/Chicago", 
                                             "America/Los_Angeles", "Asia/Seoul", "Asia/Tokyo", "Asia/Shanghai"}) {
                ZoneId tz = ZoneId.of(tzId);
                ZonedDateTime zdt = gameLdt.atZone(tz);
                long expectedMin = zdt.toEpochSecond() / 60;
                long diff = u32 - expectedMin;
                System.out.printf("  %-25s expected=%d (0x%08X) diff=%d min%n", tzId, expectedMin, expectedMin, diff);
            }
            System.out.println();
        }
    }
}
