package tv.corebuilds.iconpack.glyphs;

import android.app.Activity;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * The front door: what a user sees when they open Core Builds Glyphs.
 *
 * <p>Until this activity existed the package had no launcher entry at all, so
 * the only way to meet it was inside a launcher's icon-pack list — and a user
 * who installed it (or let the icon pack's Art style toggle install it for
 * them) had nothing that said what it was, whether it was the right version,
 * or where the toggle lived. This is that answer, and it is deliberately the
 * only screen the package has: it is a resource pack, and the catalogue,
 * wallpapers and settings are the icon pack's.
 *
 * <p>The counts are read out of this package's own generated
 * {@code res/xml/drawable.xml} and {@code res/xml/appfilter.xml} rather than
 * hard-coded, so the screen cannot drift from the art it ships. If either
 * fails to parse the line is hidden instead of showing a wrong number.
 *
 * <p>No dependencies: framework widgets and a framework theme, which is why
 * the focus ring is a state selector rather than a Material ripple.
 */
public class GlyphsHomeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.glyphs_home);

        TextView counts = findViewById(R.id.home_counts);
        int icons = countItems(R.xml.drawable, "drawable");
        int components = countItems(R.xml.appfilter, "component");
        if (icons > 0 && components > 0) {
            counts.setText(getString(R.string.glyphs_home_counts, icons, components));
        } else {
            counts.setVisibility(View.GONE);
        }

        Button open = findViewById(R.id.open_icon_pack);
        open.setOnClickListener(v -> openIconPack());
    }

    /**
     * The icon pack is where the catalogue, the wallpapers and the Art style
     * toggle live. Missing is a real state, not an error: the companion can be
     * sideloaded on its own, in which case say so rather than do nothing.
     */
    private void openIconPack() {
        Intent open = getPackageManager().getLaunchIntentForPackage(GlyphsActivity.ICON_PACK);
        if (open == null) {
            Toast.makeText(this, R.string.glyphs_home_missing_pack, Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(open);
    }

    /**
     * Count the {@code <item>} rows carrying {@code attribute} in a generated
     * XML resource. Returns -1 if it cannot be read.
     */
    private int countItems(int xmlRes, String attribute) {
        XmlResourceParser parser = null;
        try {
            parser = getResources().getXml(xmlRes);
            int count = 0;
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG
                        && "item".equals(parser.getName())
                        && parser.getAttributeValue(null, attribute) != null) {
                    count++;
                }
                event = parser.next();
            }
            return count;
        } catch (XmlPullParserException | IOException | RuntimeException e) {
            return -1;
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }
}
