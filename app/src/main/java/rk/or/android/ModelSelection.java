package rk.or.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.AdapterView.OnItemClickListener;

import rk.or.android.R;

/**
 * Activity for model selection
 */
public class ModelSelection extends Activity implements OnItemClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_model_selection);
        GridView grid = findViewById(R.id.grid);
        grid.setBackgroundResource(R.drawable.background256x256);
        grid.setAdapter(new AppsAdapter());
        grid.setOnItemClickListener(this);
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        startActivity(new Intent(this,
                ModelView.class).putExtra("model", mModels[position]));
    }

    // Thumbs are in res/drawable and raw are in src/rk/or/raw
    private final Integer[] mThumbIds = {
            R.drawable.cocotte72x72, R.drawable.duck72x72, R.drawable.boat72x72,
            R.drawable.austria72x72, R.drawable.blueyellow72x72, R.drawable.gally72x72};
    private final String[] mModels = { "cocotte", "duck", "boat", "austria", "", "" };

    // Set views for icons
    class AppsAdapter extends BaseAdapter {
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView i = new ImageView(ModelSelection.this);
            i.setImageResource(mThumbIds[position]);
            i.setScaleType(ImageView.ScaleType.FIT_CENTER);
            i.setLayoutParams(new GridView.LayoutParams(170, 170)); // 85 85 ou des icones 48x48
            return i;
        }

        public final int getCount() {
            return mThumbIds.length;
        }

        public final Object getItem(int position) {
            return position;
        }

        public final long getItemId(int position) {
            return position;
        }
    }
}
