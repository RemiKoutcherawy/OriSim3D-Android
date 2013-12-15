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

/**
 * Activity for model selection
 */
public class ModelSelection extends Activity implements OnItemClickListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_model_selection);
        GridView grid = (GridView) findViewById(R.id.grid);
        grid.setBackgroundResource(R.drawable.background256x256);
        grid.setAdapter(new AppsAdapter());
        grid.setOnItemClickListener(this);
    }
    public void onItemClick(AdapterView<?> parent, View view, int position,
        long id) {
    	// Default
    	if (View3D.front == 0) {
    		View3D.texturesON = true;
    		View3D.front = R.drawable.gally400x572; 
//    		View3D.back = R.drawable.blue32x32; 
    		View3D.back = R.drawable.sunako400x572;
    		View3D.background = R.drawable.background256x256;
    	}
    	// Change Colors Textures
    	if (id == mThumbIds.length-2) {
    		View3D.texturesON = false;
    		return;
    	}
    	if (id == mThumbIds.length-1) {
    		View3D.texturesON = true;
    		View3D.front = R.drawable.gally400x572; 
//    		View3D.back = R.drawable.blue32x32;
    		View3D.back = R.drawable.sunako400x572;
    		View3D.background = R.drawable.background256x256;
    		return;
    	}
    	startActivity(new Intent(this,rk.or.android.ModelView.class).putExtra("model", mModels[position]));
    	return;
    }

		// Thumbs are in res/drawable and models are in src/rk/or/models
    private Integer[] mThumbIds = {
        R.drawable.cocotte72x72, R.drawable.duck72x72, R.drawable.boat72x72,
        R.drawable.austria72x72, R.drawable.blueyellow72x72, R.drawable.gally72x72};
    private String[] mModels = {
    		"cocotte.txt", "duck.txt", "boat.txt", 
    		"austria.txt", "cocotte.txt", "cocotte.txt"};
    
    // Set views for icons
    public class AppsAdapter extends BaseAdapter {
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView i = new ImageView(ModelSelection.this);
            i.setImageResource(mThumbIds[position]);
            i.setScaleType(ImageView.ScaleType.FIT_CENTER);
            i.setLayoutParams(new GridView.LayoutParams(170, 170)); // 85 85 ou des icones 48x48
            return i;
        }
        public final int getCount() { return mThumbIds.length; }
        public final Object getItem(int position) { return position; }
        public final long getItemId(int position) { return position; }
    }
}
