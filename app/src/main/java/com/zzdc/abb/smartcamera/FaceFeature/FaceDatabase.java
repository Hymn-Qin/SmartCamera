package com.zzdc.abb.smartcamera.FaceFeature;

import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

@Table(database = CameraDatabase.class)
public class FaceDatabase extends BaseModel {
    @PrimaryKey(autoincrement = true)
    public int id;//通知唯一id
    @Column
    public String times;//设置通知时间，long类型
    @Column
    public byte[] face;
    @Column
    public String name;
    @Column
    public String direction;// 正脸  左侧脸  右侧脸  上侧脸  下侧脸
    @Column
    public boolean focus;

}

