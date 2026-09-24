package tv.corebuilds.iconpack.glyphs;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * The companion's only code, and it has no UI of its own.
 *
 * Launchers find an icon pack by the intent filters on one of its activities,
 * so this activity exists to carry them. What a launcher does with it:
 *
 * <ul>
 *   <li>Reads res/xml/appfilter.xml and the drawables straight out of this
 *       package. No code runs; that is the auto-apply path, and it is why this
 *       package exists: the icon pack itself maps every app to its 16:9
 *       banner, and this one maps the same apps to their square glyphs.</li>
 *   <li>Asks for a single icon (the ACTION_PICK_ICON family). The picker is
 *       the icon pack's, so the request is forwarded there with
 *       FLAG_ACTIVITY_FORWARD_RESULT: the chosen icon goes straight back to
 *       the launcher that asked, as if the icon pack had been asked
 *       directly. The forward is marked {@link #EXTRA_PICK_GLYPHS}, so the
 *       picker answers with glyphs only - asked through this pack, the
 *       launcher gets this pack's art.</li>
 *   <li>Opens the pack from its list. There is nothing to show here, so the
 *       icon pack opens instead - it is where the toggle lives.</li>
 * </ul>
 */
public class GlyphsActivity extends Activity {

    static final String ICON_PACK = "tv.corebuilds.iconpack";

    /** GlyphsCompanion.EXTRA_PICK_GLYPHS in the icon pack; kept equal by tests. */
    static final String EXTRA_PICK_GLYPHS = "tv.corebuilds.iconpack.extra.PICK_GLYPHS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        try {
            if (isPickRequest(in)) {
                Intent forward = new Intent(in);
                forward.setComponent(null);
                forward.setPackage(ICON_PACK);
                forward.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                forward.putExtra(EXTRA_PICK_GLYPHS, true);
                startActivity(forward);
            } else {
                Intent open = getPackageManager().getLaunchIntentForPackage(ICON_PACK);
                if (open != null) {
                    startActivity(open);
                }
            }
        } catch (RuntimeException e) {
            // The icon pack is missing or refused the hand-off. Answer the
            // caller rather than leave it waiting on a result.
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    static boolean isPickRequest(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        if (action == null) return false;
        if (action.endsWith("PICK_ICON")) return true;
        // Nova asks for a picker with its THEMES action plus a category.
        return "org.adw.launcher.THEMES".equals(action)
                && intent.hasCategory("com.novalauncher.category.CUSTOM_ICON_PICKER");
    }
}
