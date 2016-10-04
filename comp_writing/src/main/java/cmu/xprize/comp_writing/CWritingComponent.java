//*********************************************************************************
//
//    Copyright(c) 2016 Carnegie Mellon University. All Rights Reserved.
//    Copyright(c) Kevin Willows All Rights Reserved
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//
//*********************************************************************************

package cmu.xprize.comp_writing;

import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.support.percent.PercentRelativeLayout;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import cmu.xprize.ltkplus.CGlyphMetrics;
import cmu.xprize.ltkplus.CRecognizerPlus;
import cmu.xprize.ltkplus.CGlyphSet;
import cmu.xprize.ltkplus.IGlyphSink;
import cmu.xprize.ltkplus.CRecResult;
import cmu.xprize.util.CClassMap;
import cmu.xprize.util.CErrorManager;
import cmu.xprize.util.CLinkedScrollView;
import cmu.xprize.util.IEvent;
import cmu.xprize.util.IEventDispatcher;
import cmu.xprize.util.IEventListener;
import cmu.xprize.util.ILoadableObject;
import cmu.xprize.util.IScope;
import cmu.xprize.util.JSON_Helper;
import cmu.xprize.util.TCONST;


/**
 * TODO: document your custom view class.
 *
 *  !!!! NOTE: This requires com.android.support:percent:23.2.0' at least or the aspect ratio
 *  settings will not work correctly.
 *
 */
public class CWritingComponent extends PercentRelativeLayout implements IEventListener, IEventDispatcher, IWritingComponent, ILoadableObject {

    protected Context           mContext;
    protected char[]            mStimulusData;

    protected CLinkedScrollView mRecognizedScroll;
    protected CLinkedScrollView mDrawnScroll;

    protected LinearLayout      mRecogList;
    protected LinearLayout      mDrawnList;

    protected int               mMaxLength   = 0; //GCONST.ALPHABET.length();                // Maximum string length

    protected final Handler     mainHandler  = new Handler(Looper.getMainLooper());
    protected HashMap           queueMap     = new HashMap();
    protected boolean           _qDisabled   = false;
    protected boolean           _alwaysTrack = true;
    protected int               _fieldIndex  = 0;
    protected String            _replayType;

    protected IGlyphSink        _recognizer;
    protected CGlyphSet         _glyphSet;

    protected String            mResponse;
    protected String            mStimulus;

    protected List<String>      _data;
    protected int               _dataIndex = 0;
    protected boolean           _dataEOI   = false;
    public    String            mValue;

    public    boolean           _immediateFeedback = true;

    // json loadable
    public String[]             dataSource;

    final private String  TAG        = "CWritingController";


    public CWritingComponent(Context context) {
        super(context);
        init(context, null);
    }

    public CWritingComponent(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CWritingComponent(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    protected void init(Context context, AttributeSet attrs) {
        mContext = context;

        setClipChildren(false);
    }

    /** Note: This is used with the writing_tutor_comp layout in the dev project
     *  The resource names are different at the momoent
     *
     */
    public void onCreate() {

        CGlyphController v;
        CStimulusController r;

        // Note: this is used in the GlyphRecognizer project to initialize the sample
        //       In normal operation mMaxLength is zero here.
        //
        mStimulusData = new char[mMaxLength];

        // Setup the Recycler for the recognized input views
        mRecognizedScroll = (CLinkedScrollView) findViewById(R.id.Sresponse);
        mRecogList = (LinearLayout) findViewById(R.id.Srecognized_glyphs);

        // Note: this is used in the GlyphRecognizer project to initialize the sample
        //
        for(int i1 =0 ; i1 < mStimulusData.length ; i1++)
        {
            // create a new view
            r = (CStimulusController)LayoutInflater.from(getContext())
                                        .inflate(R.layout.recog_resp_comp, null, false);

            mRecogList.addView(r);
        }
        mRecogList.requestLayout();

        //****************************

        mDrawnScroll = (CLinkedScrollView) findViewById(R.id.SfingerWriter);
        mDrawnScroll.setClipChildren(false);

        mDrawnList = (LinearLayout) findViewById(R.id.Sdrawn_glyphs);
        mDrawnList.setClipChildren(false);

        // Note: this is used in the GlyphRecognizer project to initialize the sample
        //
        for(int i1 = 0 ; i1 < mStimulusData.length ; i1++)
        {
            // create a new view
            v = (CGlyphController)LayoutInflater.from(getContext())
                                        .inflate(R.layout.drawn_input_comp, null, false);

            v.setIsLast(i1 ==  mStimulusData.length-1);

            mDrawnList.addView(v);
            ((CGlyphController)v).setLinkedScroll(mDrawnScroll);
            ((CGlyphController)v).setWritingController(this);
        }

        // Obtain the prototype glyphs from the singleton recognizer
        //
        _recognizer = CRecognizerPlus.getInstance();
        _glyphSet   = _recognizer.getGlyphPrototypes(); //new GlyphSet(TCONST.ALPHABET);

        // Note: this is used in the GlyphRecognizer project to initialize the sample
        //
        for(int i1 = 0 ; i1 < mStimulusData.length ; i1++) {

            CGlyphController comp = (CGlyphController) mDrawnList.getChildAt(i1);

            String expectedChar = mStimulus.substring(i1,i1+1);

            comp.setExpectedChar(expectedChar);
            comp.setProtoGlyph(_glyphSet.cloneGlyph(expectedChar));
        }

// TODO: Dev only
//        mRecogList.setOnTouchListener(new RecogTouchListener());
//        mDrawnList.setOnTouchListener(new drawnTouchListener());

        mRecognizedScroll.setLinkedScroll(mDrawnScroll);
        mDrawnScroll.setLinkedScroll(mRecognizedScroll);
    }

    public void onDestroy() {

    }


    //************************************************************************
    //************************************************************************
    // IWritingController Start

    /**
     * Note that only the mDrawnList will initiate this call
     *
     * @param child
     */
    public void deleteItem(View child) {
        int index = mDrawnList.indexOfChild(child);

        mDrawnList.removeViewAt(index);
        mRecogList.removeViewAt(index);
    }


    /**
     * Note that only the mDrawnList will initiate this call
     *
     * @param child
     */
    public void addItemAt(View child, int inc) {

        CGlyphController v;
        CStimulusController r;

        int index = mDrawnList.indexOfChild(child);

        // create a new view
        r = (CStimulusController)LayoutInflater.from(getContext())
                                    .inflate(R.layout.recog_resp_comp, null, false);

        mRecogList.addView(r, index + inc);

        r.setLinkedScroll(mDrawnScroll);
        r.setWritingController(this);

        // create a new view
        v = (CGlyphController)LayoutInflater.from(getContext())
                                    .inflate(R.layout.drawn_input_comp, null, false);

        // Update the last child flag
        //
        if(index == mDrawnList.getChildCount()-1) {
            ((CGlyphController)child).setIsLast(false);
            v.setIsLast(true);
        }

        mDrawnList.addView(v, index + inc);

        v.setLinkedScroll(mDrawnScroll);
        v.setWritingController(this);
    }


    /**
     *
     */
    public void clear() {

        // Add the recognized response display containers
        //
        mRecogList.removeAllViews();

        // Add the Glyph input containers
        //
        mDrawnList.removeAllViews();
    }


    public void updateResponse(IGlyphController child, String glyph) {

        int index = mDrawnList.indexOfChild((View)child);

        CStimulusController respText = (CStimulusController)mRecogList.getChildAt(index);

        respText.setResponseChar(glyph);
    }


    /**
     * If the user taps the stimulus we try and scroll the tapped char onscreen
     *
     * @param controller
     */
    public void stimulusClicked(CStimulusController controller) {

        CGlyphController   v;

        int index = mRecogList.indexOfChild(controller);

        v = (CGlyphController)mDrawnList.getChildAt(index);

        // Capture the initiator status to force the tracker to update in the stimulus field
        //
        if(_alwaysTrack || v.hasGlyph()) {

            mDrawnScroll.captureInitiatorStatus();
            mDrawnScroll.smoothScrollTo(calcOffsetToCenterGlyph(v), 0);
            mDrawnScroll.releaseInitiatorStatus();
        }
    }


    private int calcOffsetToCenterGlyph(CGlyphController glyph) {

        int  newScroll = 0;

        int sc = mDrawnScroll.getWidth() / 2;
        int gc = glyph.getWidth() / 2;
        int gx = (int) glyph.getX();

        newScroll = gx - (sc - gc);

        return newScroll;
    }



    private int calcOffsetToMakeGlyphVisible(int scrollX, int padding) {

        int                i1 = 0;
        int                newScroll = 0;
        int                skip = padding;
        CGlyphController   v;

        for(i1 = 0 ; i1 < mDrawnList.getChildCount() ; i1++) {

            v = (CGlyphController)mDrawnList.getChildAt(i1);

            newScroll = (int) v.getX();

            if(skip <= 0 )
                break;

            // once we find a view that is visible we skip past it by skip count if possible
            //
            if(newScroll >= scrollX) {

                skip--;
            }
        }

        return newScroll;
    }


    public void autoScroll(IGlyphController glyphController) {

        CGlyphController view    = (CGlyphController) glyphController;
        int              padding = 2;

        if(view != null) {

            int sx = mDrawnScroll.getScrollX();
            int sw = mDrawnScroll.getWidth();

            int gx = (int) view.getX();
            int gw = view.getWidth() * 2;

            // If the glyph to the right of the current glyph is partially obscurred then calc
            // the offset to bring it on screen - with some padding (i.e. multiple glyph widths)
            // Capture the initiator status to force the tracker to update in the stimulus field
            //
            if((gx+gw) > (sx + sw)) {

                mDrawnScroll.captureInitiatorStatus();
                mDrawnScroll.smoothScrollTo(calcOffsetToMakeGlyphVisible(sx, padding), 0);
                mDrawnScroll.releaseInitiatorStatus();
            }
        }
    }


    // Debug component requirement
    @Override
    public void updateGlyphStats(CRecResult[] ltkPlusResult, CRecResult[] ltkresult, CGlyphMetrics metricsA, CGlyphMetrics metricsB) {
    }


    public void rippleHighlight() {

        long delay = 0;
        CStimulusController r;
        CGlyphController   v;

        for(int i1 = 0 ; i1 < mRecogList.getChildCount() ; i1++) {

            r = (CStimulusController)mRecogList.getChildAt(i1);

            r.post(TCONST.HIGHLIGHT, delay);
            delay += WR_CONST.RIPPLE_DELAY;
        }

        delay = 0;
        for(int i1 = 0 ; i1 < mDrawnList.getChildCount() ; i1++) {

            v = (CGlyphController)mDrawnList.getChildAt(i1);

            v.post(TCONST.HIGHLIGHT, delay);
            delay += WR_CONST.RIPPLE_DELAY;
        }

    }


    public void rippleReplay(String type) {

        _fieldIndex = 0;
        _replayType = type;

        replayNext();
    }
    private void replayNext() {

        CStimulusController r;
        CGlyphController   v;

        if( _fieldIndex < mDrawnList.getChildCount()) {

            v = (CGlyphController)mDrawnList.getChildAt(_fieldIndex);
            v.post(_replayType);

            _fieldIndex++;
        }
        else {
            applyBehavior(WR_CONST.REPLAY_COMPLETE);
        }
    }



    public boolean scanForPendingRecognition(IGlyphController source) {

        boolean               result = false;
        IGlyphController glyphInput;

        for(int i1 = 0 ; i1 < mDrawnList.getChildCount() ; i1++) {

            glyphInput = (IGlyphController)mDrawnList.getChildAt(i1);

            if(glyphInput != source) {

                result = glyphInput.firePendingRecognition();
            }
        }

        return result;
    }


    public void inhibitInput(IGlyphController source, boolean inhibit) {

        boolean               result = false;
        IGlyphController glyphInput;
        int                   i1 = 0;

        if(_immediateFeedback) {

            for (i1 = 0; i1 < mDrawnList.getChildCount(); i1++) {

                glyphInput = (IGlyphController) mDrawnList.getChildAt(i1);

                if (glyphInput != source) {

                    glyphInput.inhibitInput(inhibit);
                }
            }
        }
    }

    // IWritingController End
    //************************************************************************
    //************************************************************************




    //**********************************************************
    //**********************************************************
    //*****************  DataSink Interface

    public boolean dataExhausted() {
        return (_dataIndex >= _data.size())? true:false;
    }

    public void setDataSource(String[] dataSource) {

        // _data takes the form - ["92","3","146"]
        //
        _data      = new ArrayList<String>(Arrays.asList(dataSource));
        _dataIndex = 0;
        _dataEOI   = false;
    }


    public void next() {

        // May only call next on stimulus variants
        //
        try {
            if (_data != null) {
                updateText(_data.get(_dataIndex));

                _dataIndex++;
            } else {
                CErrorManager.logEvent(TAG, "Error no DataSource : ", null, false);
            }
        } catch (Exception e) {
            CErrorManager.logEvent(TAG, "Data Exhuasted: call past end of data", e, false);
        }
    }


    /**
     * @param data
     */
    public void updateText(String data) {

        CStimulusController r;
        CGlyphController    v;

        mStimulus = data;

        // Add the recognized response display containers
        //
        mRecogList.removeAllViews();

        for(int i1 =0 ; i1 < mStimulus.length() ; i1++)
        {
            // create a new view
            r = (CStimulusController)LayoutInflater.from(getContext())
                    .inflate(R.layout.recog_resp_comp, null, false);

            r.setStimulusChar(mStimulus.substring(i1, i1 + 1));

            mRecogList.addView(r);

            r.setLinkedScroll(mDrawnScroll);
            r.setWritingController(this);
        }


        // Add the Glyph input containers
        //
        mDrawnList.removeAllViews();
        mDrawnList.setClipChildren(false);

        for(int i1 =0 ; i1 < mStimulus.length() ; i1++)
        {
            // create a new view
            v = (CGlyphController)LayoutInflater.from(getContext())
                    .inflate(R.layout.drawn_input_comp, null, false);

            // Last is used for display updates - limits the extent of the baseline
            v.setIsLast(i1 ==  mStimulus.length()-1);

            String expectedChar = mStimulus.substring(i1,i1+1);

            v.setExpectedChar(expectedChar);

            if(!expectedChar.equals(" ")) {
                v.setProtoGlyph(_glyphSet.cloneGlyph(expectedChar));
            }

            mDrawnList.addView(v);

            v.setLinkedScroll(mDrawnScroll);
            v.setWritingController(this);
        }
    }



    //************************************************************************
    //************************************************************************
    // Tutor Scriptable methods  Start


    /**
     * Manage component defined (i.e. specific) events
     *
     * @param event
     * @return  true of event handled
     */
    public boolean applyBehavior(String event){

        boolean result = false;

        switch(event) {

            case WR_CONST.FIELD_REPLAY_COMPLETE:
                replayNext();
                break;
        }


        return result;
    }


    public void pointAtEraseButton() {

        IGlyphController glyphInput;

        for (int i1 = 0; i1 < mDrawnList.getChildCount(); i1++) {

            glyphInput = (IGlyphController) mDrawnList.getChildAt(i1);

            if(glyphInput.hasError()) {
                glyphInput.pointAtEraseButton();
                break;
            }
        }
    }


    public void highlightFields() {

        post(TCONST.HIGHLIGHT, 500);
    }

    // Tutor methods  End
    //************************************************************************
    //************************************************************************



    //***********************************************************
    // Event Listener/Dispatcher - Start


    /**
     * Must be Overridden in app module to access tutor engine
     * @param linkedView
     */
    @Override
    public void addEventListener(String linkedView) {
    }

    @Override
    public void dispatchEvent(IEvent event) {

        for (IEventListener listener : mListeners) {
            listener.onEvent(event);
        }
    }

    /**
     *
     * @param event
     */
    @Override
    public void onEvent(IEvent event) {

        CGlyphController v;
        CStimulusController r;

        switch(event.getType()) {

            case TCONST.FW_EOI:
                _dataEOI = true;        // tell the response that the data is exhausted
                break;
        }
    }

    // Event Listener/Dispatcher - End
    //***********************************************************



    public class RecogTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            PointF touchPt;
            final int action = event.getAction();

            touchPt = new PointF(event.getX(), event.getY());

            //Log.i(TAG, "ActionID" + action);

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    Log.i(TAG, "RECOG _ ACTION_DOWN");
                    break;
                case MotionEvent.ACTION_MOVE:
                    Log.i(TAG, "RECOG _ ACTION_MOVE");
                    break;
                case MotionEvent.ACTION_UP:
                    Log.i(TAG, "RECOG _ ACTION_UP");
                    break;
            }
            return true;
        }
    }


    public class drawnTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            PointF touchPt;
            final int action = event.getAction();

            touchPt = new PointF(event.getX(), event.getY());

            //Log.i(TAG, "ActionID" + action);

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    Log.i(TAG, "DRAWN _ ACTION_DOWN");
                    break;
                case MotionEvent.ACTION_MOVE:
                    Log.i(TAG, "DRAWN _ ACTION_MOVE");
                    break;
                case MotionEvent.ACTION_UP:
                    Log.i(TAG, "DRAWN _ ACTION_UP");
                    break;
            }
            return true;
        }
    }


    //************************************************************************
    //************************************************************************
    // Component Message Queue  -- Start


    public class Queue implements Runnable {

        protected String _command    = "";
        protected String _target     = "";
        protected String _item       = "";

        public Queue(String command) {
            _command = command;
        }

        public Queue(String command, String target) {
            _command = command;
            _target  = target;
        }

        public Queue(String command, String target, String item) {
            _command = command;
            _target  = target;
            _item    = item;
        }


        @Override
        public void run() {

            try {
                queueMap.remove(this);

                switch(_command) {

                    case WR_CONST.RIPPLE_HIGHLIGHT:
                        rippleHighlight();
                        break;

                    case WR_CONST.RIPPLE_DEMO:
                    case WR_CONST.RIPPLE_REPLAY:
                    case WR_CONST.RIPPLE_PROTO:
                        rippleReplay(_command);
                        break;

                    default:

                        break;
                }


            }
            catch(Exception e) {
                CErrorManager.logEvent(TAG, "Run Error:", e, false);
            }
        }
    }


    /**
     *  Disable the input queues permenantly in prep for destruction
     *  walks the queue chain to diaable scene queue
     *
     */
    private void terminateQueue() {

        // disable the input queue permenantly in prep for destruction
        //
        _qDisabled = true;
        flushQueue();
    }


    /**
     * Remove any pending scenegraph commands.
     *
     */
    private void flushQueue() {

        Iterator<?> tObjects = queueMap.entrySet().iterator();

        while(tObjects.hasNext() ) {
            Map.Entry entry = (Map.Entry) tObjects.next();

            mainHandler.removeCallbacks((Queue)(entry.getValue()));
        }
    }


    /**
     * Keep a mapping of pending messages so we can flush the queue if we want to terminate
     * the tutor before it finishes naturally.
     *
     * @param qCommand
     */
    private void enQueue(Queue qCommand) {
        enQueue(qCommand, 0);
    }
    private void enQueue(Queue qCommand, long delay) {

        if(!_qDisabled) {
            queueMap.put(qCommand, qCommand);

            if(delay > 0) {
                mainHandler.postDelayed(qCommand, delay);
            }
            else {
                mainHandler.post(qCommand);
            }
        }
    }

    /**
     * Post a command to the queue
     *
     * @param command
     */
    public void post(String command) {
        post(command, 0);
    }
    public void post(String command, long delay) {

        enQueue(new Queue(command), delay);
    }


    /**
     * Post a command and target to this queue
     *
     * @param command
     */
    public void post(String command, String target) {
        post(command, target, 0);
    }
    public void post(String command, String target, long delay) { enQueue(new Queue(command, target), delay); }


    /**
     * Post a command , target and item to this queue
     *
     * @param command
     */
    public void post(String command, String target, String item) {
        post(command, target, item, 0);
    }
    public void post(String command, String target, String item, long delay) { enQueue(new Queue(command, target, item), delay); }




    // Component Message Queue  -- End
    //************************************************************************
    //************************************************************************



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