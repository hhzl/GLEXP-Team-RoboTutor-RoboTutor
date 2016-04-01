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

package cmu.xprize.robotutor.tutorengine.graph;


import android.content.Intent;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import cmu.xprize.robotutor.RoboTutor;
import cmu.xprize.robotutor.tutorengine.CTutor;
import cmu.xprize.robotutor.tutorengine.CTutorEngine;
import cmu.xprize.robotutor.tutorengine.graph.vars.IScope2;
import cmu.xprize.util.IScope;
import cmu.xprize.util.TCONST;
import cmu.xprize.robotutor.tutorengine.graph.vars.TScope;


public class type_timeline extends type_action {

    private TScope                       mScope;

    private HashMap<String, CTrackLayer> mLayerMap  = new HashMap<String,CTrackLayer>();
    private HashMap<String, MediaPlayer> mTrackData = new HashMap<String,MediaPlayer>();

    private LoaderThread              _loaderThread;
    private boolean                   _isLoading     = false;
    private boolean                   _needsMap      = false;
    private HashMap<String, Integer>  _frameMap      = new HashMap<>();

    protected Timer                   _timer         = null;
    protected boolean                 _playing       = false;
    private boolean                   _deferredPlay  = false;
    protected TimerTask               _frameTask     = null;
    protected CBaseFrame              _currAudio     = null;
    protected CScriptFrame            _currScript    = null;

    protected long                    _currFrame = 0;
    protected long                    _seekFrame = 0;               // current seek point
    protected long                    _nextFrame = 0;               // next frame with an event
    protected long                    _lastFrame = 0;               // Last frame - action may beyond last event with silence
    protected String                  _trackType;                   // SEQUENTIAL | ABSOLUTE

    protected int                     _fps       = TCONST.FPS;

    private LocalBroadcastManager     bManager;

    // json loadable
    public String                    trackname;


    final static public String TAG = "type_timeline";



    public type_timeline() {

    }

//    @Override
//    public String next() {
//        String status = TCONST.NONE;
//
//        stop();
//
//        return status;
//    }

    @Override
    public String applyNode() {

        String status;

        if(_playing | _deferredPlay) {
            stop();
            status = TCONST.NONE;
        }
        else {
            play();
            status = TCONST.WAIT;
        }

        return status;
    }


    /**
     * frame timer used for Absolute timed sequences
     *
     */
    private void onNextAbsFrame() {

        // Once we hit the next event frame we fire those events.
        // This always initializes to 0:0 so we automatically seek to 0

        if(_currFrame == _nextFrame) {
            applyFrame();
        }

        if(_currFrame >= _lastFrame) {

            // Reset for next time around
            gotoAndStop(0);

            if(mode == TCONST.AUDIOFLOW)
                _scope.tutor().eventNext();
        }
        else {
            // do post increment so we catch the zero frame
            _currFrame++;
        }
    }


    /**
     * frame timer used for Absolute timed sequences
     *
     */
    private void onNextRelFrame() {

        // Once we hit the next event frame we fire those events.
        // This always initializes to 0:0 so we automatically seek to 0

        applyFrame();

        // do post increment so we catch the zero frame
        _currFrame++;
    }


    /**
     * frame timer used for Absolute timed sequences
     *
     */
    private void applyFrame() {

        seek(_currFrame);

        if(_currAudio != null)
            _currAudio.play();

        // Run the script and then clear it so it doesn't refire on the
        // next frame
        if(_currScript != null) {
            _currScript.applyScript();
            _currScript = null;
        }
    }


    @Override
    public void play() {

        switch(_trackType) {

            case TCONST.ABSOLUTE_TYPE:

                if (!_isLoading) {

                    _timer     = new Timer(trackname);
                    _frameTask = new TimerTask() {
                        @Override
                        public void run() {
                            onNextAbsFrame();
                        }
                    };
                    _timer.scheduleAtFixedRate(_frameTask, 0, 1000 / 24);

                    _playing = true;
                } else
                    _deferredPlay = true;
                break;

            case TCONST.SEQUENTIAL_TYPE:
                onNextRelFrame();
                break;
        }
    }


    @Override
    public void stop() {

        switch(_trackType) {
            case TCONST.ABSOLUTE_TYPE:
                if(_playing) {
                    if (_currAudio != null)
                        _currAudio.stop();

                    if (_frameTask != null)
                        _frameTask.cancel();

                    _timer.cancel();
                    _timer     = null;
                    _frameTask = null;

                    _playing = false;
                }
                else if(_deferredPlay)
                    _deferredPlay = false;
                break;

            case TCONST.SEQUENTIAL_TYPE:
                break;
        }
    }


    public void gotoAndPlay(int frame) {
        switch(_trackType) {
            case TCONST.ABSOLUTE_TYPE:
                gotoAndStop(frame);
                if (_currAudio != null)
                    _currAudio.seek(frame);

                play();
                break;

            case TCONST.SEQUENTIAL_TYPE:
                break;
        }
    }


    public void gotoAndStop(int frame) {
        switch(_trackType) {
            case TCONST.ABSOLUTE_TYPE:

                stop();

                // The execution point will start here on next play
                // Seek will update nextFrame during applyFrame
                _currFrame = frame;
                _nextFrame = frame;

                if (_currAudio != null)
                    _currAudio.seek(frame);
                break;

            case TCONST.SEQUENTIAL_TYPE:
                break;
        }
    }



    /**
     * This utility function finds the closest frame greater than or equal to the
     * seek point. This can be used to find the seek point or the next event past the
     * seek point. Note that in the case of a named frame with no actual script/audio/tts
     * the script frame will be returned with an empty script array.  It is possible to return
     * a frame index that is greater than seekpnt if there are no events that overlap the
     * seek point.
     *
     * @param seekPnt
     * @param recordCurr
     * @return
     */
    private int scanFrames(long seekPnt, boolean recordCurr) {

        // next event records the closest "start event" frame Greater than the seek point
        // <start event> : audio events have a start and end while scripts etc are dicrete
        // to a single frame - Start is significant as a timer is set to initiate the next event.

        int nextEvent = TCONST.MAXTRACKLENGTH;

        CTrackLayer layer = mLayerMap.get(TCONST.SCRIPT);

        for(CBaseFrame script : (ArrayList<CBaseFrame>)(layer.mframes)) {
            if(script.mIndex >= seekPnt) {
                if(recordCurr)
                    _currScript = (CScriptFrame)script;
                nextEvent   = (script.mIndex < nextEvent)? script.mIndex : nextEvent;
                break;
            }
        }


        layer = mLayerMap.get(TCONST.AUDIO);

        for(CBaseFrame script : (ArrayList<CBaseFrame>)(layer.mframes)) {

            // Keep track of where the last frame in the animation.
            if(_lastFrame < script.mLast)
                    _lastFrame = script.mLast;

            // Note that clips are ordered so we always hit the earliest audio clip first
            // and audio clips cannot overlap

            if ((script.mIndex <= seekPnt) && (script.mLast >= seekPnt)) {
                // If we are seeking to a point within an audio clip then set it up as the
                // current clip
                if(recordCurr)
                    _currAudio = (CAudioFrame) script;

                // Check if this is the next closest start event to the seek point
                if((script.mIndex >= seekPnt) && (script.mIndex < nextEvent)) {
                    nextEvent = script.mIndex;
                    break;
                }
            }
            else if(script.mIndex > seekPnt) {
                nextEvent = (script.mIndex < nextEvent) ? script.mIndex : nextEvent;
                break;
            }
        }


        return nextEvent;
    }



    public void seek(long seekPnt) {

        // reset _curr... frame items

        _currScript = null;
        _currAudio  = null;

        // record the seek point

        _seekFrame = seekPnt;

        // Find the next event frame that is coincident with the seek point.
        // If none found it returns the next frame with an event past the seek point or
        // TCONST.MAXTRACKLENGTH to indicate it is finished.
        // If there is are discrete script/MIXED frames at the seek point or audio an clip that
        // overlaps the seek point they are recorded as _curr... frames.

        _nextFrame = scanFrames(seekPnt, true);

        // If there is an event coincident with the seek point (most commonly) then we need to
        // scan for the next event past the seek point.

        if(_seekFrame == _nextFrame) {
            _nextFrame = scanFrames(seekPnt + 1, false);

        } else {
            // Otherwise there are 3 possiblities -
            // There is no audio currently but an event in the future that we need to wait for
            // An audio clip currently playing at an audio seek point and an event in the future.
            // There is no audio and no events in the future.  _nextFrame == TCONST.MAXTRACKLENGTH
        }

        if(_currAudio != null) {
            // If there is an audio track ensure that it is loaded
            ((CAudioFrame)_currAudio).mPlayer.preEnter();

            if(_currAudio.mIndex != seekPnt) {
                _currAudio.seek(seekPnt - _currAudio.mIndex);
            }
        }
    }



    // Component parts - derived from the Flash xml structure

    class CTrackLayer {

        protected String    mLayerName;
        protected List mframes    = new ArrayList<CBaseFrame>();
        protected int       mCurrFrame = 0;

        protected int       mDepth = 0;

        public CTrackLayer(XmlPullParser xpparser) throws IOException, XmlPullParserException {

            xpparser.require(XmlPullParser.START_TAG, null, "DOMLayer");

            mLayerName = xpparser.getAttributeValue(null, "name");

            while (xpparser.next() != XmlPullParser.END_TAG) {

                if (xpparser.getEventType() == XmlPullParser.END_TAG) {
                    mDepth--;
                    continue;
                }
                if (xpparser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String name = xpparser.getName();

                // Starts by looking for the entry tag
                switch(name) {
                    case "frames":
                        // just tunnel into the frames element
                        mDepth++;
                        break;

                    // Note that it is implicit that there will NEVER be a xml spec that
                    // has a mixed track and a script track -
                    case "DOMFrame":
                        switch(mLayerName) {
                            case TCONST.SCRIPT:
                                _trackType = TCONST.ABSOLUTE_TYPE;
                                mframes.add(new CScriptFrame(xpparser));
                                break;

                            case TCONST.MIXED:
                                _trackType = TCONST.SEQUENTIAL_TYPE;
                                // fall through - mixed is a special type of audio track

                            case TCONST.AUDIO:
                                // Audio tracks can have sound assets or they can use a script
                                if(xpparser.getAttributeValue(null, "soundName") == null)
                                    mframes.add(new CScriptFrame(xpparser));
                                else
                                    mframes.add(new CAudioFrame(xpparser));
                                break;

                        }
                        break;

                    default:
                        // Skip unrecognized tags
                        skip(xpparser);
                        break;
                }
            }

            // If any frames are labeled create a map - it is assumed they will be used in scripts

            if(_needsMap) {
                for(int i1 = 0 ; i1 < mframes.size() ; i1++) {
                    CBaseFrame frame = (CBaseFrame)mframes.get(i1);

                    if(frame.mName != null) {
                        _frameMap.put(frame.mName, new Integer(i1));
                    }
                }
            }
        }

        private void setName(String name) {mLayerName = name;}
        public String getName() {return mLayerName;}
    }


    class CBaseFrame {
        public String mName;
        public String mType;
        public int    mIndex;
        public int    mLast;
        public int    mDuration;

        protected void play() {}
        protected void stop() {}
        protected void seek(long frame) {}
    }


    class CScriptFrame extends CBaseFrame {

        private CFrameScript mScript;
        private ArrayList    mframes = new ArrayList();

        private int          mDepth = 0;

        public CScriptFrame(XmlPullParser xpparser) throws IOException, XmlPullParserException {

            xpparser.require(XmlPullParser.START_TAG, null, "DOMFrame");

            mName     = xpparser.getAttributeValue(null, "name");
            mIndex    = getSafeInteger(xpparser.getAttributeValue(null, "index"));
            mDuration = getSafeInteger(xpparser.getAttributeValue(null, "duration"));

            // If any of the frames have a name then we need to generate a frame map
            // to allow vectoring to named frames from scripts.

            if(mName != null)
                _needsMap = true;

            while((xpparser.next() != XmlPullParser.END_TAG) || (mDepth > 0)) {
                Log.i(TAG, "Event: " + xpparser.getName() + "  Type: " + xpparser.getEventType());

                if (xpparser.getEventType() == XmlPullParser.END_TAG) {
                    mDepth--;
                    continue;
                }
                if (xpparser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }

                String name = xpparser.getName();

                // Starts by looking for the entry tag
                switch(name) {
                    case "Actionscript":
                        // just tunnel into the Actionscript element
                        mDepth++;
                        break;

                    case "script":
                        mScript = new CFrameScript(xpparser);
                        break;

                    default:
                        // Skip unrecognized tags
                        skip(xpparser);
                        break;
                }
            }
        }

        protected void applyScript() {

            if(mScript != null)
              mScript.applyScript();
        }
    }


    class CFrameScript {
        private graph_module mScript;

        public CFrameScript(XmlPullParser xpp) throws IOException, XmlPullParserException {

            xpp.require(XmlPullParser.START_TAG, null, "script");

            while (xpp.nextToken() != XmlPullParser.END_TAG) {
                if (xpp.getEventType() != XmlPullParser.CDSECT) {
                    continue;
                }
                mScript = new graph_module();

                try {
                    // TODO : add scoping
                    mScript.loadJSON(new JSONObject(xpp.getText()), (IScope2)mScope);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        public void applyScript() {
            String _scriptState = TCONST.START;

            // When we retrieve the "next" node we automatically apply it
            // This may result in a simple state change - method call etc.
            // this returns TCONST.DONE indicating the event is complete
            //
            // It may start a process that need to complete before continuing.
            // returning TCONST.WAIT indicating that next will be driven by a
            // completion event - or some external user event.
            //
            // A result of TCONST.NONE indicated the source node is exhausted.
            // which will drive a search for the next node
            //
            // Note that within scripts in Flash tracks audio act like events not streams -
            // e.g they are fire and forget and the next event fires immediately so we
            // ignore WAIT and continue
            //
            while(!_scriptState.equals(TCONST.NONE) ||
                   _scriptState.equals(TCONST.WAIT)) {

                _scriptState = mScript.applyNode();

            };

            mScript.resetNode();
        }
    }


    /**
     * This decodes a Flash audio frame specification from an associated Flash Library object
     * xml spec.
     *
     * The frame name may be used to hold a comma delimited list of features
     *
     */
    class CAudioFrame extends CBaseFrame {

        private int          mDepth = 0;
        private String       mSoundSource;
        private type_audio mPlayer;

        public CAudioFrame(XmlPullParser xpp) throws IOException, XmlPullParserException {

            xpp.require(XmlPullParser.START_TAG, null, "DOMFrame");

            try {
                String tindex = xpp.getAttributeValue(null, "index");

                mName        = xpp.getAttributeValue(null, "name");
                mIndex       = getSafeInteger(tindex);
                mDuration    = getSafeInteger(xpp.getAttributeValue(null, "duration"));
                mLast        = mIndex + mDuration;
                mSoundSource = xpp.getAttributeValue(null, "soundName");

                // NOTE: THIS IS FLASH SPECIFIC
                // we strip off the Flash  audio/en/
                mSoundSource = mSoundSource.substring(9);

                // Note we pass the relative start location of this audio track - for seek purposes
                String jsonCode = "{\"action\":\"AUDIO\", \"command\":\"PAUSE\", \"soundsource\":\"" + mSoundSource +
                        "\",\"index\":\"" + tindex + "\", \"features\":\"" + ((mName != null)? mName:"") + "\"}";

                // If any of the frames have a name then we need to generate a frame map
                // to allow vectoring to named frames from other scripts.

                if(mName != null)
                    _needsMap = true;

                mPlayer = new type_audio();

                try {
                    mPlayer.loadJSON(new JSONObject(jsonCode), (IScope2)mScope);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            catch(Exception e) {
                Log.d(TAG, "Audio frame format error: " + e);
            }

            // We don't need the internal elements of this so skip to the end tag
            skip(xpp);
        }

        @Override
        protected void play() {
            mPlayer.play();
        }

        @Override
        protected void stop() {
            pause();
        }


        protected void pause() {
            mPlayer.pause();
        }

        @Override
        protected void seek(long frame) {
            mPlayer.seek(frame);
        }
    }



    //************ Serialization


    // Convert null to 0 as safe default.

    private int getSafeInteger(String intAsString) {
        Integer value;

        if(intAsString != null)
            value = Integer.parseInt(intAsString);
        else
            value = 0;

        return value;
    }


    //***************************************************
    //*** Resource Loader Parser

    /**
     * The RecognizerThread provides a background thread on which to do the rocognition task
     * TODO: We may need a scrim on the UI thread depending on the observed performance
     */
    class LoaderThread extends AsyncTask<Void, Void, String> {

        LoaderThread() {
            _isLoading = true;
        }

        /** This is processed on the background thread - when it returns OnPostExecute is called or
         // onCancel if it was cancelled -
         */
        @Override
        protected String doInBackground(Void... unsued) {

            loadTrack(TCONST.TUTORROOT + "/" + TCONST.TDATA + "/" + _scope.tutorName() + "/" + trackname + ".xml");
            Log.d(TAG, "ActionTrack Loaded");

            return null;
        }


        /** OnPostExecute is guaranteed to run on the UI thread so we can update the view etc
         // TODO: update this to do something useful
         */
        @Override
        protected void onPostExecute(String sResponse) {

            _isLoading = false;

            if(_deferredPlay) {
                _deferredPlay = false;
                play();
            }
            // Let anyone interested know there is a new recognition set available
            bManager.sendBroadcast(new Intent(TCONST.LOADCOMPLETE));
        }


        /**
         *  The recognizer expects an "array" of strokes so we generate that here in the UI thread
         *  from the ArrayList of captured strokes.
         *
         */
        @Override
        protected void onPreExecute() {
        }
    }


    public void loadTrack(String factoryPATH)  {

        InputStream in     = null;
        int         mDepth = 0;

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);

            XmlPullParser xpparser = factory.newPullParser();

            try {
                if (CTutorEngine.CacheSource.equals(TCONST.ASSETS)) {

                    in = _scope.tutor().openAsset(factoryPATH);

                } else {
                    String filePath = RoboTutor.EXTERNFILES + "/" + factoryPATH;

                    in = new FileInputStream(filePath);
                }
            }
            catch(Exception e) {
                Log.e(TAG, "ERROR: Flash resource: " + e);
                System.exit(1);
            }

            xpparser.setInput(in, null);

            int eventType = xpparser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if(eventType == XmlPullParser.START_DOCUMENT) {
                    Log.i(TAG, "XPP_INFO: Start document:" + xpparser.getName());

                } else if(eventType == XmlPullParser.START_TAG) {
                    Log.i(TAG, "XPP_INFO: Start TAG: " + xpparser.getName());

                    String name = xpparser.getName();

                    // Starts by looking for the entry tag
                    switch(name) {
                        case "DOMSymbolItem":
                        case "timeline":
                        case "DOMTimeline":
                        case "layers":
                            // Decompose Flash Object declaration
                            // just tunnel into these elements
                            // outer tags - they are superfluous for our use
                            mDepth++;
                            break;

                        case "DOMLayer":
                            CTrackLayer nlayer = new CTrackLayer(xpparser);
                            mLayerMap.put(nlayer.getName(), nlayer);
                            break;

                        default:
                            // Skip unrecognized tags
                            skip(xpparser);
                            break;
                    }

                } else if(eventType == XmlPullParser.END_TAG) {
                    Log.i(TAG, "XPP_INFO: End TAG: " + xpparser.getName());
                    mDepth--;

                } else if(eventType == XmlPullParser.TEXT) {
                    Log.i(TAG, "XPP_INFO: Element Text: " + xpparser.getText());
                }
                eventType = xpparser.next();
            }

        } catch (XmlPullParserException e) {
            Log.e(TAG, "ERROR: XML Spec Invalid: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            Log.e(TAG, "ERROR: XML Spec Invalid: " + e.getMessage());
            System.exit(1);
        }
    }


    /**
     * Process arbitrary field strings
     *
     * @param parser
     * @param field
     * @return
     * @throws IOException
     * @throws XmlPullParserException
     */
    private String readField(XmlPullParser parser, String field) throws IOException, XmlPullParserException {

        String strData = null;

        parser.require(XmlPullParser.START_TAG, null, field);

        if (parser.next() == XmlPullParser.TEXT) {
            strData = parser.getText();
            parser.nextTag();
        }
        parser.require(XmlPullParser.END_TAG, null, field);

        return strData;
    }


    /**
     * Skip TAGs that we don't know about
     *
     * @param parser
     * @throws XmlPullParserException
     * @throws IOException
     */
    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }



    // *** Serialization

    @Override
    public void loadJSON(JSONObject jsonObj, IScope2 scope) {

        super.loadJSON(jsonObj, scope);

        mScope = new TScope(scope.tutor(), name, (TScope)scope);

        _trackType = TCONST.ABSOLUTE_TYPE;

        // Capture the local broadcast manager
        bManager = LocalBroadcastManager.getInstance(CTutorEngine.Activity);

        // Tasks can only run once so create a new one for each recognition task.
        _loaderThread = new LoaderThread();
        _loaderThread.execute();
    }

}

