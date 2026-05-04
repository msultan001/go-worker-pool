package com.mycompany.file.distributed.system.ui;

import javafx.beans.property.*;

// file details
public class FileEntry {

    private final StringProperty name;
    private final LongProperty   size;

    public FileEntry(String name, long size) {
        this.name = new SimpleStringProperty(name);
        this.size = new SimpleLongProperty(size);
    }

    // fix for table
    public StringProperty nameProperty() { return name; }
    public LongProperty   sizeProperty() { return size; }

    public String getName() { return name.get(); }
    public long   getSize() { return size.get(); }

    // make size look good
    public String getFormattedSize() {
        long s = size.get();
        if (s < 0)                return "?";
        if (s < 1_024)            return s + " B";
        if (s < 1_048_576)        return String.format("%.1f KB", s / 1_024.0);
        if (s < 1_073_741_824L)   return String.format("%.1f MB", s / 1_048_576.0);
        return                           String.format("%.2f GB", s / 1_073_741_824.0);
    }
}
