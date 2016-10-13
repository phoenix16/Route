package com.apps.prakriti.route;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.FrameLayout;

public class TouchWrapper extends FrameLayout
{
    private final static String TAG = MapDirectionsAsyncTask.class.getSimpleName();
    private MapsActivity myObject;

    public TouchWrapper(Context context)
    {
        super(context);
        myObject = (MapsActivity) context;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event)
    {

        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                MapsActivity.mMapIsTouched = true;
                Log.d(TAG, "!!!!!!!!!!!!!! TOUCH EVENT !!!!!!!!!!!!!!");
                myObject.startIdentify();
                break;

            case MotionEvent.ACTION_UP:
                MapsActivity.mMapIsTouched = false;
                break;
        }
        return super.dispatchTouchEvent(event);
    }
}