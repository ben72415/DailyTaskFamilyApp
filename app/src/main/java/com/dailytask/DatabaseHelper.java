package com.dailytask;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "DailyTask.db";
    private static final int DATABASE_VERSION = 6;

    public static final String TABLE_TASKS = "tasks";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_MEMBER = "member";
    public static final String COLUMN_DATE = "task_date";
    public static final String COLUMN_TIME = "task_time";
    public static final String COLUMN_NOTES = "task_notes";
    public static final String COLUMN_STATUS = "task_status";
    public static final String COLUMN_IMAGE = "task_image";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TASKS_TABLE = "CREATE TABLE " + TABLE_TASKS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_TITLE + " TEXT,"
                + COLUMN_MEMBER + " TEXT,"
                + COLUMN_DATE + " TEXT,"
                + COLUMN_TIME + " TEXT,"
                + COLUMN_NOTES + " TEXT,"
                + COLUMN_STATUS + " TEXT,"
                + COLUMN_IMAGE + " TEXT" + ")"; // 建立 Table 時加入圖片欄位
        db.execSQL(CREATE_TASKS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TASKS);
        onCreate(db);
    }

    //
    public void addTask(String title, String member, String date, String time, String notes, String image) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, title);
        values.put(COLUMN_MEMBER, member);
        values.put(COLUMN_DATE, date);
        values.put(COLUMN_TIME, time);
        values.put(COLUMN_NOTES, notes);
        values.put(COLUMN_STATUS, "未完成"); // 預設初始狀態
        values.put(COLUMN_IMAGE, image);     // 🚀 修正：成功將圖片路徑持久化存在本地！

        long result = db.insert(TABLE_TASKS, null, values);
        db.close();
    }

    //
    public boolean updateTask(int id, String newNotes, String newStatus, String newImage) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NOTES, newNotes);
        values.put(COLUMN_STATUS, newStatus);
        values.put(COLUMN_IMAGE, newImage); // 更新附件路徑

        int rows = db.update(TABLE_TASKS, values, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return rows > 0;
    }

    public void deleteTask(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_TASKS, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public List<Task> getTasksByDate(String date) {
        List<Task> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_TASKS + " WHERE " + COLUMN_DATE + " = ? ORDER BY " + COLUMN_TIME + " ASC", new String[]{date});
        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                String member = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MEMBER));
                String taskDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE));
                String taskTime = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIME));
                String taskNotes = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTES));
                String taskStatus = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS));
                String taskImage = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE));

                list.add(new Task(String.valueOf(id), title, member, taskDate, taskTime, taskNotes, taskStatus, taskImage));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return list;
    }
}