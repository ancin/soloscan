package org.soloquest.soloscan.dataset;

import java.util.Map;

public class MapRow implements Row {

    private final Map<String, Object> map;

    public MapRow(Map<String, Object> map) {
        this.map = map;
    }

    @Override
    public Object getValue(String column) {
        return map.get(column);
    }

    @Override
    public boolean putValue(String column, Object value) {
        return map.putIfAbsent(column, value) == null;
    }

    @Override
    public boolean containsColumn(String column) {
        return map.containsKey(column);
    }

    @Override
    public String toString() {
        return "MapRow{" +
                "map=" + map +
                '}';
    }
}
