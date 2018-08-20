package com.zzdc.abb.smartcamera.FaceFeature;

import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

@Table(database = CameraDatabase.class)
public class AlertVideoData extends BaseModel {
    @PrimaryKey(autoincrement = true)
    public int id;//通知唯一id
    @Column
    public String times;//设置通知时间，long类型
    @Column
    public String start;
    @Column
    public long startTime;
    @Column
    public String end;
    @Column
    public long endTime;
    @Column
    public String filePath;
    @Column
    public String imagePath;
}
