package cmu.xprize.mn_component;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.percent.PercentRelativeLayout;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;

import cmu.xprize.util.ILoadableObject;
import cmu.xprize.util.IScope;
import cmu.xprize.util.JSON_Helper;
import cmu.xprize.util.TCONST;

public class CMn_Component extends LinearLayout implements ILoadableObject{

    private   Context      mContext;
    private   float        mAlleyRadius;
    private   int          mAlleyMargin = 3;
    protected String       mDataSource;

    private   ArrayList<CMn_Alley> _alleys = new ArrayList<>();
    private   int                  _dataIndex = 0;
    private   int                  _mnindex;
    private   int                  _difValue;
    private   int                  _corValue;


    // json loadable
    public CMn_Data[]      dataSource;

    static final String TAG = "CMn_Component";
    private boolean correct;


    public CMn_Component(Context context) {
        super(context);
        init(context, null);
    }

    public CMn_Component(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CMn_Component(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }


    public void init(Context context, AttributeSet attrs) {

        mContext = context;

        if(attrs != null) {

            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.CMn_Component,
                    0, 0);

            try {
                mAlleyRadius = a.getFloat(R.styleable.CMn_Component_radAlley, 0.25f);
                mDataSource  = a.getString(R.styleable.CMn_Component_dataSource);
            } finally {
                a.recycle();
            }
        }
    }


    public void setDataSource(CMn_Data[] _dataSource) {

        dataSource = _dataSource;
        _dataIndex = 0;
    }


    public void next() {

        try {
            if (dataSource != null) {
                updateDataSet(dataSource[_dataIndex]);

                _dataIndex++;
            } else {
                Log.e(TAG, "Error no DataSource : ");
                System.exit(1);
            }
        }
        catch(Exception e) {
            Log.e(TAG, "Data Exhuasted: call past end of data");
            System.exit(1);
        }

    }


    public boolean dataExhausted() {
        return (_dataIndex >= dataSource.length)? true:false;
    }


    private void updateDataSet(CMn_Data data) {

        int delta = data.dataset.length -_alleys.size();

        // More alleys than we need
        if(delta < 0) {
            while(delta > 0) {
                trimAlley();
                delta--;
            }
        }
        // Fewer alleys than we need
        else if(delta > 0) {
            while(delta > 0) {
                addAlley();
                delta--;
            }
        }

        // decode the index of the missing number
        //
        switch(data.mn_index) {
            case TCONST.RAND:
                _mnindex = (int)(Math.random() * data.dataset.length);
                break;

            case TCONST.MINUSONE:
                for(int i1 = 0 ; i1 < data.dataset.length ; i1++) {
                    if(data.dataset[i1] == 0) {
                        _mnindex = i1;
                        break;
                    }
                }
                break;

            default:
                _mnindex = Integer.parseInt(data.mn_index);
                break;
        }

        // Calc the correct value
        // We make he assumption that any two consequtive numbers may be used to
        // calculate the interval and that there are a minimum of 3 numbers
        //
        switch(_mnindex) {
            case 0:
                _difValue = Math.abs(data.dataset[1] - data.dataset[2]);
                _corValue = data.dataset[1] - _difValue;
                break;

            case 1:
                _difValue = Math.abs(data.dataset[0] - data.dataset[2]);
                _corValue = data.dataset[2] - _difValue;
                break;

            default:
                _difValue = Math.abs(data.dataset[0] - data.dataset[1]);
                _corValue = data.dataset[_mnindex-1] + _difValue;
                break;
        }


        // Apply the dataset to the alleys
        for(int i1 = 0 ; i1 < data.dataset.length ; i1++) {
            _alleys.get(i1).setData(data, i1, _mnindex);
        }
    }


    protected boolean isCorrect() {

        boolean correct = _alleys.get(_mnindex).isCorrect(_corValue);

        return correct;
    }


    private CMn_Alley addAlley() {

        // Defining the layout parameters of the TextView
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT);

        lp.weight     = 1;
        lp.leftMargin = mAlleyMargin;

        // Setting the parameters on the TextView
        CMn_Alley alley = new CMn_Alley(mContext);
        alley.setLayoutParams(lp);

        _alleys.add(alley);

        addView(alley);

        return alley;
    }


    private void trimAlley() {

       removeView(_alleys.get(_alleys.size()-1));

        _alleys.remove(_alleys.size() - 1);
    }


    private void delAllAlley() {

        for(CMn_Alley alley: _alleys) {
            removeView(alley);
        }

        _alleys.clear();
    }


    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
    }



    //************ Serialization


    /**
     * Load the data source
     *
     * @param jsonData
     */
    @Override
    public void loadJSON(JSONObject jsonData, IScope scope) {

        JSON_Helper.parseSelf(jsonData, this, CClassMap.classMap, scope);
        _dataIndex = 0;
    }

}