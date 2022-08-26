package com.mobiquity.calendar;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.provider.CalendarContract.Calendars;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.getcapacitor.PluginResult;
import com.google.gson.Gson;

@NativePlugin(requestCodes = {2, 1,101,102,103,200,201,202}, permissions = {Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR})
public class CapacitorCalendar extends Plugin {
    static final Integer RESULT_CODE_OPENCAL = 1;
    static final Integer RESULT_CODE_CREATE = 2;

    // write permissions
    private static final int PERMISSION_REQCODE_CREATE_EVENT = 100;
    private static final int PERMISSION_REQCODE_DELETE_EVENT = 101;
    private static final int PERMISSION_REQCODE_DELETE_EVENT_BY_ID = 102;
    private static final int PERMISSION_REQCODE_UPDATE_EVENT = 103;

    // read permissions
    private static final int PERMISSION_REQCODE_OPEN_CALENDAR = 200;
    private static final int PERMISSION_REQCODE_FIND_EVENTS = 201;
    private static final int PERMISSION_REQCODE_GET_AVAILABLE_CALENDARS = 202;

    public static final String LOG_TAG = "Calendar";

    String eventIdParam = null;
    String calendarIdParam = null;
    String titleParam = null;
    String locationParam = null;
    String notesParam = null;
    Long startFromParam = 0L;
    Long startToParam = 0L;

    protected enum KeyIndex {
        CALENDARS_ID,
        IS_PRIMARY,
        CALENDARS_NAME,
        CALENDARS_VISIBLE,
        CALENDARS_PRIMARY,
        CALENDARS_DISPLAY_NAME,
        EVENTS_ID,
        EVENTS_CALENDAR_ID,
        EVENTS_DESCRIPTION,
        EVENTS_LOCATION,
        EVENTS_SUMMARY,
        EVENTS_START,
        EVENTS_END,
        EVENTS_RRULE,
        EVENTS_ALL_DAY,
        INSTANCES_ID,
        INSTANCES_EVENT_ID,
        INSTANCES_BEGIN,
        INSTANCES_END,
        ATTENDEES_ID,
        ATTENDEES_EVENT_ID,
        ATTENDEES_NAME,
        ATTENDEES_EMAIL,
        ATTENDEES_STATUS
    }
    private static final String[] CALENDAR_PROJECTION =  new String[]{
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE
    };
    private static final String[] EVENT_PROJECTION = new String[]{"_id", "title", "eventLocation", "eventStatus", "dtstart", "dtend", "duration", "allDay", "availability", "rrule", "displayColor", "visible"};
    private static final int PROJECTION_ID_INDEX = 0;
    private static final int PROJECTION_TITLE_INDEX = 1;
    private static final int PROJECTION_EVENT_LOCATION_INDEX = 2;
    private static final int PROJECTION_STATUS_INDEX = 3;
    private static final int PROJECTION_DTSTART_INDEX = 4;
    private static final int PROJECTION_DTEND_INDEX = 5;
    private static final int PROJECTION_DURATION_INDEX = 6;
    private static final int PROJECTION_ALL_DAY_INDEX = 7;
    private static final int PROJECTION_AVAILABILITY_INDEX = 8;
    private static final int PROJECTION_RRULE_INDEX = 9;
    private static final int PROJECTION_DISPLAY_COLOR_INDEX = 10;
    private static final int PROJECTION_VISIBLE_INDEX = 11;
    public static final String EXTRA_CALENDAR_ID = "com.hanmajid.yggr.android.calenderprovider.calendaractivity.extra";

    private void hasReadPermission(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("hasReadPermission", calendarPermissionGranted(Manifest.permission.READ_CALENDAR));
        call.resolve(ret);
    }

    private void hasWritePermission(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("hasWritePermission", calendarPermissionGranted(Manifest.permission.WRITE_CALENDAR));
        call.resolve(ret);
    }

    private void hasReadWritePermission(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("hasReadWritePermission", calendarPermissionGranted(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR));
        call.resolve(ret);
    }

    private void requestReadPermission(int requestCode) {
        requestPermission(requestCode, Manifest.permission.READ_CALENDAR);
    }

    private void requestWritePermission(int requestCode) {
        requestPermission(requestCode, Manifest.permission.WRITE_CALENDAR);
    }

    private void requestPermissionsCalendar(int requestCode) {
        requestPermission(requestCode, Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR);
    }

    private boolean calendarPermissionGranted(String... types) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        for (final String type : types) {
            if (!hasPermission(type)) {
                return false;
            }
        }
        return true;
    }

    private void requestPermission(int requestCode, String... types) {
        if (!calendarPermissionGranted(types)) {
            pluginRequestPermissions(types, requestCode);
        }
    }
    @Override
    protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.handleRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean permissionsGranted = true;
        for (int grantResult: grantResults) {
            if (grantResult != 0) {
                permissionsGranted = false;
            }
        }

        PluginCall savedCall = getSavedCall();
        if (!permissionsGranted) {
            savedCall.reject("permission failed");
        }

        if (requestCode == PERMISSION_REQCODE_CREATE_EVENT) {
            createEvent(savedCall);
        } else if (requestCode == PERMISSION_REQCODE_DELETE_EVENT) {
            deleteEvent(savedCall);
        } else if (requestCode == PERMISSION_REQCODE_DELETE_EVENT_BY_ID) {
            deleteCalendarEventById(savedCall);
        } else if (requestCode == PERMISSION_REQCODE_UPDATE_EVENT) {
            updateEvent(savedCall);
        } else if (requestCode == PERMISSION_REQCODE_OPEN_CALENDAR) {
            openCalendar(savedCall);
        } else if (requestCode == PERMISSION_REQCODE_FIND_EVENTS) {
            findEvent(savedCall);
        } else if (requestCode == PERMISSION_REQCODE_GET_AVAILABLE_CALENDARS) {
            getAvailableCalendars(savedCall);
        }

    }
    public boolean hasRequiredPermissions1()
    {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || this.getContext().checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }
    public boolean hasRequiredReadPermissions()
    {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || this.getContext().checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    @PluginMethod()
    public void openCalendar(PluginCall call) {
        saveCall(call);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.getContext().checkSelfPermission(Manifest.permission.WRITE_CALENDAR);
        }
        if (!hasRequiredPermissions1()) {
            requestReadPermission(PERMISSION_REQCODE_OPEN_CALENDAR);
        } else {
            try {
                JSObject data = call.getData();
                final long millis = data.has("date") ? data.getLong("date") : new Date().getTime();

                Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
                builder.appendPath("time");
                ContentUris.appendId(builder, millis);
                final Intent intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
                this.startActivityForResult(call, intent, RESULT_CODE_OPENCAL);

                call.success();
            } catch (JSONException e) {
                System.err.println("Exception: " + e.getMessage());
                call.error(e.getMessage());
            }
        }
    }

    @PluginMethod()
    public void createEvent(PluginCall call) {
        saveCall(call);
        if (!hasRequiredPermissions1()) {
            requestWritePermission(PERMISSION_REQCODE_CREATE_EVENT);
        } else {
            createCalendarEvent(call);
        }
    }

    protected void createCalendarEvent(PluginCall call) {
        ContentResolver cr = this.getContext().getContentResolver();
        ContentValues values = new ContentValues();
        JSObject data = call.getData();

        try {
            long startTime = data.has("startDate") ? data.getLong("startDate") : new Date().getTime();
            long endTime = data.has("endDate") ? data.getLong("endDate") : new Date().getTime();
            final boolean hasAllDayEventConfig = data.has("allDay");

            if (hasAllDayEventConfig != false) {
                final boolean allDayEventConfig = data.getBoolean("allDay");
                values.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
                values.put(Events.ALL_DAY, allDayEventConfig);
                values.put(Events.DTSTART, startTime);
                values.put(Events.DTEND, endTime);
            } else {
                final boolean allDayEvent = isAllDayEvent(new Date(startTime), new Date(endTime));
                if (allDayEvent) {
                    values.put(Events.EVENT_TIMEZONE, "UTC");
                    values.put(Events.ALL_DAY, true);
                    values.put(Events.DTSTART, startTime + TimeZone.getDefault().getOffset(startTime));
                    values.put(Events.DTEND, endTime + TimeZone.getDefault().getOffset(endTime));
                } else {
                    values.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
                    values.put(Events.ALL_DAY, false);
                    values.put(Events.DTSTART, startTime);
                    values.put(Events.DTEND, endTime);
                }
            }

            String selectedCalendarId = data.has("calendarId") ? data.getString("calendarId").replaceAll("\"", "") : "";
            List<String> activeCalendars = Arrays.asList(getActiveCalendarIds());
            int calendarId = data.has("calendarId") && activeCalendars.contains(selectedCalendarId)
                    ? Integer.parseInt(selectedCalendarId)
                    : getDefaultCalendarId();

            values.put(Events.TITLE, call.getString("title", ""));
            values.put(Events.DESCRIPTION, call.getString("notes", ""));
            values.put(Events.EVENT_LOCATION, call.getString("location", ""));
            values.put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY);
            values.put(Events.CALENDAR_ID, calendarId);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to parse data", e);
            call.reject(e.getMessage());
        }

        try {
            Uri uri = cr.insert(Events.CONTENT_URI, values);
            String createdEventID = uri.getLastPathSegment();
            Log.d(LOG_TAG, "Created event with ID " + createdEventID);

            JSObject ret = new JSObject();
            ret.put("id", createdEventID);
            call.resolve(ret);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Permission denied", e);
            call.error(e.getMessage());
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to create an event", e);
            call.error(e.getMessage());
        }
    }

    protected int getDefaultCalendarId() throws Exception {
        int calendarId = 0;

        List<Calendar> calendars = getAvailableCalendarsList();

        if (calendars != null && calendars.size() > 0) {
            for (Calendar calendar : calendars) {
                if (calendar.defaultCalendar == true) {
                    calendarId = Integer.parseInt(calendar.id.trim());
                }
            }
        } else {
            throw new Exception("No calendars found.");
        }

        return calendarId;
    }

    @PluginMethod()
    public void findEvent(PluginCall call) {
        saveCall(call);
        if (!hasRequiredReadPermissions()) {
            requestReadPermission(PERMISSION_REQCODE_FIND_EVENTS);
        } else {
            findCalendarEvents(call);
        }
    }

    protected void findCalendarEvents(PluginCall call) {
        JSObject data = call.getData();
        JSObject ret = new JSObject();
        Long now = new Date().getTime();
        try {
            eventIdParam = data.getString("id", null);
            calendarIdParam = data.getString("calendarId", null);
            titleParam = data.getString("title", null);
            locationParam = data.getString("location", null);
            notesParam = data.getString("notes", null);
            startFromParam = data.has("startDate") ? data.getLong("startDate") : null;
            startToParam = data.has("endDate") ? data.getLong("endDate") : null;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to parse data", e);
            call.reject(e.getMessage());
        }

        Uri uri = Events.CONTENT_URI;
        try {
            JSONArray result = new JSONArray();
            new Handler().post(() -> {
                try {
                    String selection = "";
                    List<String> selectionList = new ArrayList<String>();
                    ContentResolver resolver = this.getContext().getApplicationContext().getContentResolver();

                    if (calendarIdParam != null) {
                        selection += Events.CALENDAR_ID + " = ?";
                        selectionList.add(calendarIdParam);
                    } else {
                        Cursor cursor = resolver.query(
                                Calendars.CONTENT_URI,
                                CALENDAR_PROJECTION,
                                Calendars.VISIBLE + "=1", null, null
                        );
                        selection += Events.CALENDAR_ID + " IN (";
                        String prefix = "";

                        String[] calendarIds = null;
                        if (cursor.moveToFirst()) {
                            calendarIds = new String[cursor.getCount()];
                            int i = 0;
                            do {
                                int col = cursor.getColumnIndex(Calendars._ID);
                                calendarIds[i] = cursor.getString(col);
                                selection += prefix;
                                prefix = ",";
                                selection += calendarIds[i];
                                i += 1;
                            } while (cursor.moveToNext());
                            cursor.close();
                        }

                        selection += ")";
                    }

                    if (eventIdParam != null) {
                        selection += Instances.EVENT_ID + " = ?";
                        selectionList.add(eventIdParam);

                    } else {
                        if (titleParam != null) {
                            if (!TextUtils.isEmpty(selection)) {
                                selection += " AND ";
                            }
                            selection += Events.TITLE + " LIKE ?";
                            selectionList.add("%" + titleParam + "%");
                        }
                        if (locationParam!=null) {
                            if (!TextUtils.isEmpty(selection)) {
                                selection += " AND ";
                            }
                            selection += Events.EVENT_LOCATION + " LIKE ?";
                            selectionList.add("%" + locationParam + "%");
                        }
                        if (notesParam!=null) {
                            if (!TextUtils.isEmpty(selection)) {
                                selection += " AND ";
                            }
                            selection += Events.DESCRIPTION + " LIKE ?";
                            selectionList.add("%" + notesParam + "%");
                        }
                        if (startFromParam != null) {
                            if (!TextUtils.isEmpty(selection)) {
                                selection += " AND ";
                            }
                            selection += " deleted = 0 AND " + Events.DTSTART  + " >= ?";
                            selectionList.add("%" + startFromParam + "%");
                        }
                        if (startToParam != null) {
                            if (!TextUtils.isEmpty(selection)) {
                                selection += " AND ";
                            }
                            selection += " deleted = 0 AND " + Events.DTEND + " <= ?";
                            selectionList.add("%" + startToParam + "%");
                        }
                    }
                    String[] selectionArgs = new String[selectionList.size()];
                    Cursor cur1 = resolver.query(
                            uri,
                            EVENT_PROJECTION,
                            selection, selectionList.toArray(selectionArgs), null
                    );


                    while (cur1.moveToNext()) {
                        JSONObject obj = new JSONObject();
                        long eventId1 = cur1.getLong(PROJECTION_ID_INDEX);
                        String title1 = cur1.getString(PROJECTION_TITLE_INDEX);
                        String eventLocation1 = cur1.getString(PROJECTION_EVENT_LOCATION_INDEX);
                        String status1 = cur1.getString(PROJECTION_STATUS_INDEX);
                        long dtStart1 = cur1.getLong(PROJECTION_DTSTART_INDEX);
                        long dtEnd1 = cur1.getLong(PROJECTION_DTEND_INDEX);
                        String duration1 = cur1.getString(PROJECTION_DURATION_INDEX);
                        boolean allDay1 = cur1.getInt(PROJECTION_ALL_DAY_INDEX) == 1;
                        int availability1 = cur1.getInt(PROJECTION_AVAILABILITY_INDEX);
                        String rRule1 = cur1.getString(PROJECTION_RRULE_INDEX);
                        int displayColor1 = cur1.getInt(PROJECTION_DISPLAY_COLOR_INDEX);
                        boolean visible1 = cur1.getInt(PROJECTION_VISIBLE_INDEX) == 1;

                        obj.put("id", eventId1);
                        obj.put("title", title1);
                        obj.put("eventLocation", eventLocation1);
                        obj.put("status", status1);
                        obj.put("dtStart", dtStart1);
                        obj.put("dtEnd", dtEnd1);
                        obj.put("duration", duration1);
                        obj.put("allDay", allDay1);
                        obj.put("availability", availability1);
                        obj.put("rRule", rRule1);
                        obj.put("displayColor", displayColor1);
                        obj.put("visible", visible1);

                        if (!TextUtils.isEmpty(rRule1)) {
                            obj.put("recurring", true);
                            String[] rrule_rules = rRule1.split(";");
                            JSONObject objRecurrence = new JSONObject();
                            for (String rule : rrule_rules) {
                                String rule_type = rule.split("=")[0];
                                if (rule_type.equals("FREQ")) {
                                    objRecurrence.putOpt("freq", rule.split("=")[1]);
                                } else if (rule_type.equals("INTERVAL")) {
                                    objRecurrence.putOpt("interval", rule.split("=")[1]);
                                } else if (rule_type.equals("WKST")) {
                                    objRecurrence.putOpt("wkst", rule.split("=")[1]);
                                } else if (rule_type.equals("BYDAY")) {
                                    objRecurrence.putOpt("byday", rule.split("=")[1]);
                                } else if (rule_type.equals("BYMONTHDAY")) {
                                    objRecurrence.putOpt("bymonthday", rule.split("=")[1]);
                                } else if (rule_type.equals("UNTIL")) {
                                    objRecurrence.putOpt("until", rule.split("=")[1]);
                                } else if (rule_type.equals("COUNT")) {
                                    objRecurrence.putOpt("count", rule.split("=")[1]);
                                } else {
                                    Log.d(LOG_TAG, "Missing handler for " + rule);
                                }
                                obj.put("recurrence", objRecurrence);
                            }
                        } else {
                            obj.put("recurring", false);
                        }
                        result.put(obj);
                    }
                    cur1.close();
                } catch (JSONException e) {
                    call.reject(e.getMessage());
                }
                ret.put("events", result);
                call.resolve(ret);
            });
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            call.reject(e.getMessage());
        }
    }

    @PluginMethod()
    public void deleteEvent(PluginCall call) {
        saveCall(call);
        if (!hasRequiredPermissions1()) {
            requestWritePermission(PERMISSION_REQCODE_DELETE_EVENT);
        } else {
            deleteCalendarEvents(call);
        }
    }

    protected void deleteCalendarEvents(PluginCall call) {
        ContentResolver cr = this.getContext().getContentResolver();
        JSObject data = call.getData();
        JSObject ret = new JSObject();
        long now = new Date().getTime();

        String eventId = null;
        String title = null;
        String location = null;
        String notes = null;
        long startFrom = 0L;
        long startTo = 0L;

        try {
            eventId = data.getString("id", null);
            title = data.getString("title", null);
            location = data.getString("location", null);
            notes = data.getString("notes", null);

            startFrom = data.has("startDate") ? data.getLong("startDate") : now - DateUtils.DAY_IN_MILLIS * 1000;
            startTo = data.has("endDate") ? data.getLong("endDate") : now + DateUtils.DAY_IN_MILLIS * 1000;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to parse data", e);
            call.reject(e.getMessage());
        }

        Event[] events = fetchEventInstances(eventId, title, location, notes, startFrom, startTo);
        int nrDeletedRecords = 0;
        if (events != null) {
            for (Event event : events) {
                Uri eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, Integer.parseInt(event.eventId));
                nrDeletedRecords += cr.delete(eventUri, null, null);
            }
        }

        ret.put("result", nrDeletedRecords > 0);
        call.resolve(ret);
    }

    @PluginMethod()
    public void deleteEventById(PluginCall call) {
        saveCall(call);
        if (!hasRequiredPermissions1()) {
            requestWritePermission(PERMISSION_REQCODE_DELETE_EVENT_BY_ID);
        } else {
            deleteCalendarEventById(call);
        }
    }

    protected void deleteCalendarEventById(PluginCall call) {
        JSObject data = call.getData();
        JSObject ret = new JSObject();
        String id = data.getString("id", null);

        if (id == null)
            throw new IllegalArgumentException("Event id not specified.");

        long evDtStart = -1;
        {
            Cursor cur = queryEvents(new String[]{Events.DTSTART},
                    Events._ID + " = ?",
                    new String[]{id},
                    Events.DTSTART);
            if (cur.moveToNext()) {
                evDtStart = cur.getLong(0);
            }
            cur.close();
        }
        if (evDtStart == -1)
            throw new RuntimeException("Could not find event.");

        int deleted = this.getContext().getContentResolver()
                .delete(ContentUris.withAppendedId(Events.CONTENT_URI, Long.valueOf(id)), null, null);

        ret.put("result", deleted > 0);
        call.resolve(ret);
    }

    @PluginMethod()
    public void updateEvent(PluginCall call) {
        saveCall(call);
        if (!hasRequiredPermissions1()) {
            requestWritePermission(PERMISSION_REQCODE_UPDATE_EVENT);
        } else {
            updateCalendarEvent(call);
        }
    }

    protected void updateCalendarEvent(PluginCall call) {
        JSObject data = call.getData();
        JSObject ret = new JSObject();
        String id = data.getString("id", null);

        if (id == null)
            throw new IllegalArgumentException("Event id not specified.");

        long evDtStart = -1;
        long evDtEnd = -1;
        String title = null;
        String description = null;
        String location = null;
        {
            Cursor cur = queryEvents(new String[]{Events.DTSTART, Events.DTEND, Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION},
                    Events._ID + " = ?",
                    new String[]{id},
                    Events.DTSTART);
            if (cur.moveToNext()) {
                evDtStart = cur.getLong(0);
                evDtEnd = cur.getLong(1);
                title = cur.getString(2);
                description = cur.getString(3);
                location = cur.getString(4);
            }
            cur.close();
        }
        if (evDtStart == -1)
            throw new RuntimeException("Could not find event.");

        ContentValues values = new ContentValues();
        try {
            long startTime = data.has("startDate") ? data.getLong("startDate") : evDtStart;
            long endTime = data.has("endDate") ? data.getLong("endDate") : evDtEnd;
            final boolean hasAllDayEventConfig = data.has("allDay");

            if (hasAllDayEventConfig != false) {
                final boolean allDayEventConfig = data.getBoolean("allDay");
                values.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
                values.put(Events.ALL_DAY, allDayEventConfig);
                values.put(Events.DTSTART, startTime);
                values.put(Events.DTEND, endTime);
            } else {
                final boolean allDayEvent = isAllDayEvent(new Date(startTime), new Date(endTime));
                if (allDayEvent) {
                    values.put(Events.EVENT_TIMEZONE, "UTC");
                    values.put(Events.ALL_DAY, true);
                    values.put(Events.DTSTART, startTime + TimeZone.getDefault().getOffset(startTime));
                    values.put(Events.DTEND, endTime + TimeZone.getDefault().getOffset(endTime));
                } else {
                    values.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
                    values.put(Events.ALL_DAY, false);
                    values.put(Events.DTSTART, startTime);
                    values.put(Events.DTEND, endTime);
                }
            }

            values.put(Events.TITLE, data.has("title") ? data.getString("title") : title);
            values.put(Events.DESCRIPTION, data.has("notes") ? data.getString("notes") : description);
            values.put(Events.EVENT_LOCATION, data.has("location") ? data.getString("location") : location);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to parse data", e);
            call.reject(e.getMessage());
        }

        try {
            int updated = this.getContext().getContentResolver()
                    .update(ContentUris.withAppendedId(Events.CONTENT_URI, Long.valueOf(id)), values, null, null);
            ret.put("result", updated > 0);
            call.resolve(ret);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Permission denied", e);
            call.error(e.getMessage());
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to create an event", e);
            call.error(e.getMessage());
        }
    }

    protected EnumMap<KeyIndex, String> initContentProviderKeys() {
        EnumMap<KeyIndex, String> keys = new EnumMap<KeyIndex, String>(KeyIndex.class);
        keys.put(KeyIndex.CALENDARS_ID, Calendars._ID);
        keys.put(KeyIndex.IS_PRIMARY, Calendars.IS_PRIMARY);
        keys.put(KeyIndex.CALENDARS_NAME, Calendars.NAME);
        keys.put(KeyIndex.CALENDARS_DISPLAY_NAME, Calendars.CALENDAR_DISPLAY_NAME);
        keys.put(KeyIndex.CALENDARS_VISIBLE, Calendars.VISIBLE);
        keys.put(KeyIndex.CALENDARS_PRIMARY, Calendars.IS_PRIMARY);
        keys.put(KeyIndex.EVENTS_ID, Events._ID);
        keys.put(KeyIndex.EVENTS_CALENDAR_ID, Events.CALENDAR_ID);
        keys.put(KeyIndex.EVENTS_DESCRIPTION, Events.DESCRIPTION);
        keys.put(KeyIndex.EVENTS_LOCATION, Events.EVENT_LOCATION);
        keys.put(KeyIndex.EVENTS_SUMMARY, Events.TITLE);
        keys.put(KeyIndex.EVENTS_START, Events.DTSTART);
        keys.put(KeyIndex.EVENTS_END, Events.DTEND);
        keys.put(KeyIndex.EVENTS_RRULE, Events.RRULE);
        keys.put(KeyIndex.EVENTS_ALL_DAY, Events.ALL_DAY);
        keys.put(KeyIndex.INSTANCES_ID, Instances._ID);
        keys.put(KeyIndex.INSTANCES_EVENT_ID, Instances.EVENT_ID);
        keys.put(KeyIndex.INSTANCES_BEGIN, Instances.BEGIN);
        keys.put(KeyIndex.INSTANCES_END, Instances.END);
        return keys;
    }

    protected Event[] fetchEventInstances(String eventId, String title, String location, String notes, long startFrom, long startTo) {
        String[] projection = {
                this.getKey(KeyIndex.INSTANCES_ID),
                this.getKey(KeyIndex.INSTANCES_EVENT_ID),
                this.getKey(KeyIndex.INSTANCES_BEGIN),
                this.getKey(KeyIndex.INSTANCES_END)
        };

        String sortOrder = this.getKey(KeyIndex.INSTANCES_BEGIN) + " ASC, " + this.getKey(KeyIndex.INSTANCES_END) + " ASC";
        String selection = "";
        List<String> selectionList = new ArrayList<String>();

        if (eventId != null && !"".equals(eventId)) {
            selection += Instances.EVENT_ID + " = ?";
            selectionList.add(eventId);
        } else {
            if (title != null && !"".equals(title)) {
                selection += Events.TITLE + " LIKE ?";
                selectionList.add("%" + title + "%");
            }
            if (location != null && !"".equals(location)) {
                selection += " AND " + Events.EVENT_LOCATION + " LIKE ?";
                selectionList.add("%" + location + "%");
            }
            if (notes != null && !"".equals(notes)) {
                selection += " AND " + Events.DESCRIPTION + " LIKE ?";
                selectionList.add("%" + notes + "%");
            }
        }

        String[] selectionArgs = new String[selectionList.size()];
        Cursor cursor = queryEventInstances(startFrom, startTo, projection, selection, selectionList.toArray(selectionArgs), sortOrder);

        if (cursor == null) {
            return null;
        }
        Event[] instances = null;
        if (cursor.moveToFirst()) {
            int idCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_ID));
            int eventIdCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_EVENT_ID));
            int beginCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_BEGIN));
            int endCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_END));
            int count = cursor.getCount();
            int i = 0;
            instances = new Event[count];
            do {
                instances[i] = new Event();
                instances[i].id = cursor.getString(idCol);
                instances[i].eventId = cursor.getString(eventIdCol);
                instances[i].startDate = cursor.getString(beginCol);
                instances[i].endDate = cursor.getString(endCol);
                i += 1;
            } while (cursor.moveToNext());
        }

        if ((instances == null || instances.length == 0) && eventId != null) {
            return fetchEventInstances(null, title, location, notes, startFrom, startTo);
        } else {
            return instances;
        }
    }

    protected String getKey(KeyIndex index) {
        this.initContentProviderKeys();
        return this.initContentProviderKeys().get(index);
    }

    protected Map<String, Event> fetchEventsAsMap(Event[] instances, String calendarId) {
        // Only selecting from active calendars, no active calendars = no events.
        List<String> activeCalendarIds = Arrays.asList(getActiveCalendarIds());

        if (activeCalendarIds.isEmpty()) {
            return null;
        }

        List<String> calendarsToSearch;

        if (calendarId != null) {
            calendarsToSearch = new ArrayList<String>();
            if (activeCalendarIds.contains(calendarId)) {
                calendarsToSearch.add(calendarId);
            }

        } else {
            calendarsToSearch = activeCalendarIds;
        }

        if (calendarsToSearch.isEmpty()) {
            return null;
        }


        String[] projection = new String[]{
                this.getKey(KeyIndex.EVENTS_ID),
                this.getKey(KeyIndex.EVENTS_DESCRIPTION),
                this.getKey(KeyIndex.EVENTS_LOCATION),
                this.getKey(KeyIndex.EVENTS_SUMMARY),
                this.getKey(KeyIndex.EVENTS_START),
                this.getKey(KeyIndex.EVENTS_END),
                this.getKey(KeyIndex.EVENTS_RRULE),
                this.getKey(KeyIndex.EVENTS_ALL_DAY)
        };
        // Get all the ids at once from active calendars.
        StringBuffer select = new StringBuffer();
        select.append(this.getKey(KeyIndex.EVENTS_ID) + " IN (");
        select.append(instances[0].eventId);
        for (int i = 1; i < instances.length; i++) {
            select.append(",");
            select.append(instances[i].eventId);
        }
        select.append(") AND " + this.getKey(KeyIndex.EVENTS_CALENDAR_ID) + " IN (");

        String prefix = "";
        for (String calendarToFilterId : calendarsToSearch) {
            select.append(prefix);
            prefix = ",";
            select.append(calendarToFilterId);
        }

        select.append(")");
        Cursor cursor = queryEvents(projection, select.toString(), null, null);
        Map<String, Event> eventsMap = new HashMap<String, Event>();
        if (cursor.moveToFirst()) {
            int[] cols = new int[projection.length];
            for (int i = 0; i < cols.length; i++) {
                cols[i] = cursor.getColumnIndex(projection[i]);
            }

            do {
                Event event = new Event();
                event.id = cursor.getString(cols[0]);
                event.message = cursor.getString(cols[1]);
                event.location = cursor.getString(cols[2]);
                event.title = cursor.getString(cols[3]);
                event.startDate = cursor.getString(cols[4]);
                event.endDate = cursor.getString(cols[5]);

                String rrule = cursor.getString(cols[6]);
                if (!TextUtils.isEmpty(rrule)) {
                    event.recurring = true;
                    String[] rrule_rules = cursor.getString(cols[6]).split(";");
                    for (String rule : rrule_rules) {
                        String rule_type = rule.split("=")[0];
                        if (rule_type.equals("FREQ")) {
                            event.recurrenceFreq = rule.split("=")[1];
                        } else if (rule_type.equals("INTERVAL")) {
                            event.recurrenceInterval = rule.split("=")[1];
                        } else if (rule_type.equals("WKST")) {
                            event.recurrenceWeekstart = rule.split("=")[1];
                        } else if (rule_type.equals("BYDAY")) {
                            event.recurrenceByDay = rule.split("=")[1];
                        } else if (rule_type.equals("BYMONTHDAY")) {
                            event.recurrenceByMonthDay = rule.split("=")[1];
                        } else if (rule_type.equals("UNTIL")) {
                            event.recurrenceUntil = rule.split("=")[1];
                        } else if (rule_type.equals("COUNT")) {
                            event.recurrenceCount = rule.split("=")[1];
                        } else {
                            Log.d(LOG_TAG, "Missing handler for " + rule);
                        }
                    }
                } else {
                    event.recurring = false;
                }
                event.allDay = cursor.getInt(cols[7]) != 0;
                eventsMap.put(event.id, event);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return eventsMap;
    }

    @PluginMethod()
    public void getAvailableCalendars(PluginCall call) {
        saveCall(call);
        if (!hasRequiredPermissions1()) {
            requestReadPermission(PERMISSION_REQCODE_GET_AVAILABLE_CALENDARS);
        } else {
            List<Calendar> availableCalendars = getAvailableCalendarsList();
            JSObject ret = new JSObject();
            ret.put("availableCalendars", new Gson().toJson(availableCalendars));
            call.success(ret);
        }
    }

    protected List<Calendar> getAvailableCalendarsList() {
        Cursor cursor = queryCalendars(new String[]{
                        this.getKey(KeyIndex.CALENDARS_ID),
                        this.getKey(KeyIndex.CALENDARS_PRIMARY),
                        this.getKey(KeyIndex.CALENDARS_NAME),
                        this.getKey(KeyIndex.CALENDARS_DISPLAY_NAME)
                },
                this.getKey(KeyIndex.CALENDARS_VISIBLE) + "=1", null, null);

        List<Calendar> availableCalendars = new ArrayList<>();
        boolean defaultSelected = false;
        if (cursor.moveToFirst()) {
            do {
                int col = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_ID));
                int primaryCol = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_PRIMARY));
                int nameCol = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_NAME));
                int displayNameCol = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_DISPLAY_NAME));

                if (primaryCol != -1) {
                    boolean defaultCalendar = false;
                    if (defaultSelected == false && cursor.getInt(primaryCol) == 1) {
                        defaultSelected = true;
                        defaultCalendar = true;
                    }

                    Calendar data = new Calendar(cursor.getString(col), cursor.getString(nameCol),
                            cursor.getString(displayNameCol), defaultCalendar);
                    availableCalendars.add(data);
                }
            } while (cursor.moveToNext());
            cursor.close();
        }
        return availableCalendars;
    }

    protected String[] getActiveCalendarIds() {
        Cursor cursor = queryCalendars(new String[]{
                        this.getKey(KeyIndex.CALENDARS_ID),
                        this.getKey(KeyIndex.CALENDARS_PRIMARY)
                },
                this.getKey(KeyIndex.CALENDARS_VISIBLE) + "=1", null, null);
        String[] calendarIds = null;
        if (cursor.moveToFirst()) {
            calendarIds = new String[cursor.getCount()];
            int i = 0;
            do {
                int col = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_ID));
                int primaryCol = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_PRIMARY));
                if (primaryCol != -1) {
                    calendarIds[i] = cursor.getString(col);
                    i += 1;
                }
            } while (cursor.moveToNext());
            cursor.close();
        }
        return calendarIds;
    }

    protected Cursor queryEventInstances(long startFrom, long startTo, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, startFrom);
        ContentUris.appendId(builder, startTo);

        return this.getContext().getContentResolver().query(
                builder.build(), projection, selection, selectionArgs, sortOrder);
    }

    protected Cursor queryCalendars(String[] projection, String selection,
                                    String[] selectionArgs, String sortOrder) {
        try {
            return this.getContext().getContentResolver().query(
                    Calendars.CONTENT_URI, projection, selection, selectionArgs, sortOrder);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Permission denied", e);
            return null;
        }

    }

    protected Cursor queryEvents(String[] projection, String selection,
                                 String[] selectionArgs, String sortOrder) {
        try {
            return this.getContext().getContentResolver().query(
                    Events.CONTENT_URI, projection, selection, selectionArgs, sortOrder);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Permission denied", e);
            return null;
        }
    }

    public static boolean isAllDayEvent(final Date startDate, final Date endDate) {
        return ((endDate.getTime() - startDate.getTime()) % (24 * 60 * 60 * 1000) == 0);
    }
}
