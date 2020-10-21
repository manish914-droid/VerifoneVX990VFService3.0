package com.example.verifonevx990app.utils;

import java.io.Serializable;


public class ReversalRes implements Serializable
{
    public PackageWriterModel getData() {
        return data;
    }

    public void setData(PackageWriterModel data) {
        this.data = data;
    }

    PackageWriterModel data;
}
