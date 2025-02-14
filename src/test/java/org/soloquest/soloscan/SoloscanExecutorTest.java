package org.soloquest.soloscan;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.soloquest.soloscan.dataset.DataSet;
import org.soloquest.soloscan.dataset.ListDataSet;
import org.soloquest.soloscan.exception.ExpressionCompileException;
import org.soloquest.soloscan.exception.ExpressionExecuteException;
import org.soloquest.soloscan.runtime.lang.Numbers;
import org.soloquest.soloscan.utils.MetricUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeoutException;

@Slf4j
public class SoloscanExecutorTest {

    static List<Map<String, Object>> data;

    @BeforeClass
    public static synchronized void beforeClass() throws IOException {
        if(data != null){
            return ;
        }
        data = new ArrayList<>();
        long start = System.currentTimeMillis();
        InputStream stream = SoloscanExecutorTest.class.getClassLoader().getResourceAsStream("data.xlsx");
        XSSFWorkbook workbook = new XSSFWorkbook(stream);

        log.warn("read execl:{}.ms",(System.currentTimeMillis()-start));

        start = System.currentTimeMillis();
        Sheet sheet = workbook.getSheetAt(0);
        int rowNums = sheet.getPhysicalNumberOfRows();
        log.warn("rowNums:{}",rowNums);

        start = System.currentTimeMillis();
        Row row = sheet.getRow(0);
        List<String> key = new ArrayList<>();
        for (Cell cell : row) {
            cell.setCellType(CellType.STRING);
            key.add(cell.getStringCellValue());
        }
        for(int i=1;i<rowNums;i++){
            row = sheet.getRow(i);
            if(row!=null){
                Map<String,Object> map = new HashMap<>();
                int count = 0;
                for(int col = 0;col< key.size();col++){
                    Cell cell = row.getCell(col);
                    if(cell != null){
                        cell.setCellType(CellType.STRING);
                        String value = cell.getStringCellValue();
                        if(value!=null && !value.equals("")){
                            int intValue = Integer.parseInt(value);
                            map.put(key.get(count),intValue);
                        }
                    }else{
                        map.put(key.get(count),null);
                    }
                    count++;
                }

                if(count!= key.size()){
                    throw new RuntimeException("rowNums:"+i+"count:"+count+",key size:"+key.size());
                }
                data.add(map);
            }
        }
        log.warn("generate map:"+(System.currentTimeMillis()-start)+".ms");
        SoloscanOptions.set(SoloscanOptions.GENERATE_CLASS.key(), true);
    }


    @Test
    public void testExperiment() {
        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        List<String> list = new ArrayList<>();
        list.add("{count(SCCC)}");
        list.add("{count(SCCC),grouping(SCCC,RQ),SCCC=20}");
        list.add("{count(SCCC),grouping(SCCC),SCCC=20}");
        list.add("{count(SCCC!=20)/count()}");
        list.add("{count(SCCC),SCCC,SCCC=5||SCCC=11||SCCC=20}");
        log.warn("result:{}",instance.executeList(list, new ListDataSet<>(data)));

    }

    @Test
    public void testNoGrouping() {
        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        Map<String, String> map = new HashMap<>();
        map.put("row1", "{count(SCCC)} ");
        map.put("row2", "{count(SCCC!=20)/count()}");
        Map<String, Object> result = instance.execute(map, new ListDataSet<>(data));
        Assert.assertEquals(result.size(), 2);
        Assert.assertEquals(result.get("row1").getClass(), Long.class);
        Assert.assertEquals(result.get("row2").getClass(), Double.class);
    }

    @Test
    public void testCountAndCountblank() {
        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        Map<String, String> map = new HashMap<>();
        map.put("row1", "{count(SCCC)} ");
        map.put("row2", "{count(SCCC!=20)}");
        map.put("row3", "{count(SCCC!=20 || SCCC =20)}");
        Map<String, Object> result = instance.execute(map, new ListDataSet<>(data));
        Assert.assertEquals(result.size(), 3);
        Assert.assertEquals(result.get("row1").getClass(), Long.class);
        Assert.assertEquals(result.get("row2").getClass(), Long.class);
        Assert.assertEquals(result.get("row3").getClass(), Long.class);
        Assert.assertTrue(Numbers.equiv(result.get("row1"),result.get("row3")));
        Assert.assertTrue(Numbers.gt(result.get("row1"),result.get("row2")));


        map.put("row1", "{count(SCCC in [5,19,20])} ");
        map.put("row2", "{count(SCCC in [5,19])}");
        map.put("row3", "{count(SCCC in [5])}");
        result = instance.execute(map, new ListDataSet<>(data));
        Assert.assertEquals(result.size(), 3);
        Assert.assertEquals(result.get("row1").getClass(), Long.class);
        Assert.assertEquals(result.get("row2").getClass(), Long.class);
        Assert.assertEquals(result.get("row3").getClass(), Long.class);
        Assert.assertTrue(Numbers.gt(result.get("row1"),result.get("row2")));
        Assert.assertTrue(Numbers.gt(result.get("row2"),result.get("row3")));


        map.put("row1", "{countblank(SCCC)} ");
        map.put("row2", "{count(SCCC!=null)}");
        result = instance.execute(map, new ListDataSet<>(data));
        Assert.assertEquals(result.get("row1").getClass(), Long.class);
        Assert.assertEquals(result.get("row2").getClass(), Long.class);
        Assert.assertTrue(Numbers.equiv(result.get("row1"),result.get("row2")));
    }

    @Test
    public void testSumAndSumx(){

        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        Object object = instance.execute("{count(SCCC)}", new ListDataSet<>(data));
        Object object1 = instance.execute("{count(SCCC=xyz)}", new ListDataSet<>(data));
        Object object2 = instance.execute("{count(SCCC!=xyz)}", new ListDataSet<>(data));
        Assert.assertEquals(((Long) object).longValue(), ((Long) object1).longValue() + ((Long) object2).longValue());

        object = instance.execute("{sum(SCCC)}", new ListDataSet<>(data));
        object1 = instance.execute("{sum(SCCC)+sum(SCCC)}", new ListDataSet<>(data));
        Assert.assertEquals(((Long) object).longValue() * 2, ((Long) object1).longValue());

        object = instance.execute("{sum(SCCC),SCCC}", new ListDataSet<>(data));
        object1 = instance.execute("{sum(SCCC),SCCC,SCCC=1} union {sum(SCCC),SCCC,SCCC!=1}", new ListDataSet<>(data));
        Assert.assertTrue(object instanceof HashMap);
        Assert.assertTrue(object1 instanceof HashMap);
        Assert.assertEquals(object, object1);

        object = instance.execute("{sum(SCCC),SCCC,SCCC=5||SCCC=20}", new ListDataSet<>(data));
        object1 = instance.execute("{sum(SCCC),SCCC,SCCC=5} union {sum(SCCC),SCCC,SCCC=20}", new ListDataSet<>(data));
        Assert.assertTrue(object instanceof HashMap);
        Assert.assertTrue(object1 instanceof HashMap);
        Assert.assertTrue(((HashMap) object).size() > 0);
        Assert.assertTrue(((HashMap) object1).size() > 0);
        Assert.assertEquals(object, object1);

        object = instance.execute("{sum(SCCC)}", new ListDataSet<>(data));
        object1 = instance.execute("{sum(SCCC=xyz)}", new ListDataSet<>(data));
        object2 = instance.execute("{sum(SCCC!=xyz)}", new ListDataSet<>(data));
        Assert.assertEquals(((Long) object).longValue(), ((Long) object1).longValue() + ((Long) object2).longValue());

        object = instance.execute("{sum(SCCC)}", new ListDataSet<>(data));
        object1 = instance.execute("{sumx(SCCC,SCCC=xyz)}", new ListDataSet<>(data));
        object2 = instance.execute("{sumx(SCCC,SCCC!=xyz)}", new ListDataSet<>(data));
        Assert.assertEquals(((Long) object).longValue(), ((Long) object1).longValue() + ((Long) object2).longValue());


        object = instance.execute("{sum(SCCC),SCCC,SCCC=1 || SCCC=2}", new ListDataSet<>(data));
        object1 = instance.execute("{sum(SCCC),SCCC,SCCC=1}", new ListDataSet<>(data));
        object2 = instance.execute("{sum(SCCC),SCCC,SCCC=2}", new ListDataSet<>(data));
        Object object3 = instance.execute("{sum(SCCC),SCCC,SCCC=1} union {sum(SCCC),SCCC,SCCC=2}", new ListDataSet<>(data));
        Assert.assertTrue(object instanceof HashMap);
        Assert.assertTrue(object1 instanceof HashMap);
        Assert.assertTrue(object2 instanceof HashMap);
        Assert.assertEquals(((HashMap) object).size(), 2);
        Assert.assertEquals(((HashMap) object1).size(), 1);
        Assert.assertEquals(((HashMap) object2).size(), 1);
        Assert.assertEquals(object, MetricUtils.union(object1, object2));
        Assert.assertEquals(object, object3);
    }

    @Test
    public void testMinAndMinx() {

        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        Object object1 = instance.execute("{min(SCCC)}", new ListDataSet<>(data));
        Object object2 = instance.execute("{minx(SCCC,SCCC=xyz)}", new ListDataSet<>(data));
        Object object3 = instance.execute("{minx(SCCC,SCCC!=xyz)}", new ListDataSet<>(data));
        Assert.assertTrue(object1 instanceof Long);
        Assert.assertTrue(object2 instanceof Long);
        Assert.assertTrue(object3 instanceof Long);
        Assert.assertTrue(((Long) object1).longValue() == ((Long) object2).longValue() || ((Long) object1).longValue() == ((Long) object3).longValue());
    }

    @Test
    public void testAvgAndAvgx() {

        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        Object object1 = instance.execute("{average(SCCC)}", new ListDataSet<>(data));
        Assert.assertTrue(object1 instanceof Double);
        Object object2 = instance.execute("{averageX(SCCC,SCCC>"+((Double)object1).doubleValue()+")}", new ListDataSet<>(data));
        Assert.assertTrue(object2 instanceof Double);
        if(Numbers.gt(object1,0)) {
            Assert.assertTrue(((Double) object2).doubleValue() > ((Double) object1).doubleValue());
        }
    }

    @Test
    public void testMaxAndMaxx() {
        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        Object object1 = instance.execute("{max(SCCC)}", new ListDataSet<>(data));
        Object object2 = instance.execute("{maxx(SCCC,SCCC=xyz)}", new ListDataSet<>(data));
        Object object3 = instance.execute("{maxx(SCCC,SCCC!=xyz)}", new ListDataSet<>(data));
        Assert.assertTrue(object1 instanceof Long);
        Assert.assertTrue(object2 instanceof Long);
        Assert.assertTrue(object3 instanceof Long);
        Assert.assertTrue(((Long) object1).longValue() == ((Long) object2).longValue() || ((Long) object1).longValue() == ((Long) object3).longValue());
    }


    @Test
    public void testHaveGrouping() {
        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        Map<String, String> map = new HashMap<>();
        map.put("row1", "{count(SCCC),SCCC,SCCC=5||SCCC=11}");
        map.put("row2", "{count(SCCC)/count(),SCCC,SCCC=5||SCCC=11||SCCC=20}");
        Map<String, Object> result = instance.execute(map, new ListDataSet<>(data));
        Assert.assertEquals(result.size(), 2);
        Assert.assertEquals(result.get("row1").getClass(), HashMap.class);
        Assert.assertEquals(result.get("row2").getClass(), HashMap.class);
        Assert.assertEquals(((HashMap) result.get("row1")).size(), 2);
        Assert.assertEquals(((HashMap) result.get("row2")).size(), 3);
    }

    @Test
    public void testMultiExpression() {
        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        Map<String, String> map = new HashMap<>();
        Map<String, Object> result=null;
        map.put("row1", "{count(SCCC),SCCC,SCCC=5||SCCC=11||SCCC=20}");
        map.put("row2", "{count(SCCC),SCCC,SCCC=5;count(SCCC),SCCC,SCCC=5} union {count(SCCC),SCCC,SCCC=11;count(SCCC),SCCC,SCCC=5} union {count(SCCC),SCCC,SCCC=20}");
        result = instance.execute(map, new ListDataSet<>(data));
        Assert.assertEquals(result.size(), 2);
        Assert.assertEquals(result.get("row1").getClass(), HashMap.class);
        Assert.assertEquals(result.get("row2").getClass(), HashMap.class);
        Assert.assertEquals(result.get("row1"), result.get("row2"));


        map.put("row1", "{count(SCCC),SCCC}");
        map.put("row2", "{count(SCCC),SCCC} union {count(SCCC),SCCC}");
        map.put("row3", "{count(SCCC),SCCC} union {count(SCCC),SCCC}");
        result = instance.execute(map, new ListDataSet<>(data));
        Assert.assertEquals(result.size(), 3);
        Assert.assertEquals(result.get("row1").getClass(), HashMap.class);
        Assert.assertEquals(result.get("row2").getClass(), HashMap.class);
        Assert.assertEquals(result.get("row3").getClass(), HashMap.class);
        Assert.assertEquals(result.get("row1"), result.get("row2"));
        Assert.assertEquals(result.get("row1"), result.get("row3"));


        map.put("row1", "{count(SCCC)/count(S3),SCCC,SCCC=5||SCCC=11||SCCC=20}");
        map.put("row2", "{count(SCCC),SCCC,SCCC=5||SCCC=11||SCCC=20} / {count(S3),SCCC,SCCC=5||SCCC=11||SCCC=20}");
        result = instance.execute(map, new ListDataSet<>(data));
        Assert.assertEquals(result.size(), 3);
        Assert.assertEquals(result.get("row1").getClass(), HashMap.class);
        Assert.assertEquals(result.get("row2").getClass(), HashMap.class);
        Assert.assertEquals(result.get("row1"), result.get("row2"));
    }

    @Test
    public void testSlide() {
        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        Map<String, String> map = new HashMap<>();
        map.put("row1", "{count(QD2f2_1 in [4,5])/count(QA1_2=1),week,week in range(500,520,1)}");
        map.put("row2", "slide({count(QD2f2_1 in [4,5])/count(QA1_2=1),week,week in range(500,520,1)},4)");
        Map<String, Object> result = instance.execute(map, new ListDataSet<>(data));
        Assert.assertEquals(result.size(), 2);
        Assert.assertEquals(result.get("row1").getClass(), HashMap.class);
        Assert.assertEquals(result.get("row2").getClass(), HashMap.class);
    }

    @Test
    public void testFilterHasMethod() {
        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        Map<String, String> map = new HashMap<>();
        Map<String, Object> result;
        Object row1,row2;
        map.put("row1", "{count(SCCC),SCCC,SCCC=5||SCCC=11}");
        map.put("row2", "{count(SCCC),SCCC,SCCC in [5,11]}");
        result = instance.execute(map, new ListDataSet<>(data));
        Assert.assertEquals(result.size(), 2);
        row1 = result.get("row1");
        row2 = result.get("row2");
        Assert.assertEquals(row1.getClass(), HashMap.class);
        Assert.assertEquals(row2.getClass(), HashMap.class);
        Assert.assertEquals(row1,row2);

        map.put("row1", "{count(SCCC=5||SCCC=11),SCCC}");
        map.put("row2", "{count(),SCCC,SCCC in [5,11]}");
        result = instance.execute(map, new ListDataSet<>(data));
        Assert.assertEquals(result.size(), 2);
        row1 = result.get("row1");
        row2 = result.get("row2");
        Assert.assertEquals(row1.getClass(), HashMap.class);
        Assert.assertEquals(row2.getClass(), HashMap.class);
        assertEqualsAfterRemoveZero(row1,row2);

        map.put("row1", "{count(SCCC in [5,11]),SCCC}");
        map.put("row2", "{count(),SCCC,SCCC in [5,11]}");
        result = instance.execute(map, new ListDataSet<>(data));
        Assert.assertEquals(result.size(), 2);
        row1 = result.get("row1");
        row2 = result.get("row2");
        Assert.assertEquals(row1.getClass(), HashMap.class);
        Assert.assertEquals(row2.getClass(), HashMap.class);
        assertEqualsAfterRemoveZero(row1,row2);

    }

    @Test
    public void testAddCalcColumn() {
        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        DataSet dataSet = new ListDataSet<>(data);
        dataSet.addCalcColumn("newColumn", row -> {
            Object obj = row.getValue("SCCC");
            if (obj == null) {
                return "x";
            }
            try {
                int i = Integer.parseInt(obj.toString());
                if (i < 20) {
                    return "111";
                } else {
                    return "222";
                }
            } catch (Exception e) {
                return "xxx";
            }

        });
        Object result = instance.execute("{count(SCCC),newColumn}", dataSet);
        Assert.assertEquals(result.getClass(), HashMap.class);
        Assert.assertTrue(((HashMap) result).size() <= 3);
    }

    @Test
    public void testCompileException() {
        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        try {
            instance.execute("{count(SCCC);SCCC=1}a", new ListDataSet<>(data));
            Assert.fail();
        } catch (ExpressionCompileException e) {
        }

        try {
            instance.execute("{count(SCCC),SCCC=1", new ListDataSet<>(data));
            Assert.fail();
        } catch (ExpressionCompileException e) {
        }


        try {
            instance.execute("count(SCCC)+SCCC=1;=", new ListDataSet<>(data));
            Assert.fail();
        } catch (ExpressionCompileException e) {
        }
    }

    @Test
    public void testExecuteException() {
        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        try {
            instance.execute("count(SCCC)+1", new ListDataSet<>(data));
            Assert.fail();
        } catch (ExpressionExecuteException eee) {
        }
        try {
            instance.execute("{count(SCCC)-2}", new ListDataSet<>(data));
            Assert.fail();
        } catch (ExpressionExecuteException eee) {
        }

        try{
            Map<String,String> map = new HashMap<>();
            map.put("row1", "{count(QA2_8 =1)/count(QA2_8 in [0,1]),no_existed_group;count(QA2_8!=null),no_existed_group}");
            map.put("row2", "{count(QA2_8 =1)/count(QA2_8 in [0,1]),no_existed_group,;count(QA2_8!=null),no_existed_group,}");
            instance.execute(map, new ListDataSet<>(data));
            Assert.fail();
        }catch (ExpressionExecuteException eee){

        }
    }


    @Test
    public void testExecuteWithPlaceHold() {
        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        Map<String, String> map = new HashMap<>();
        Map<String, Object> result;
        map.put("row1", "{count(SCCC),SCCC,SCCC=5} union {count(SCCC),SCCC,SCCC=11} union {count(SCCC),SCCC,SCCC=9 }");
        map.put("row2", "{count(SCCC),SCCC,SCCC in [5,11,9]}");
        result = instance.execute(map, new ListDataSet<>(data));
        Object row1 = result.get("row1");
        Object row2 = result.get("row2");

        map.put("row3", "{count({{key1}}),SCCC,SCCC=5} union {count(SCCC),SCCC,SCCC=11} union {count(SCCC),SCCC,SCCC=9} ");
        map.put("row4", "{count({{key1}}),SCCC,SCCC in [{{key2}}]}");
        Map<String, String> replaceHoldMap = new HashMap<>();
        replaceHoldMap.put("key1","SCCC");
        replaceHoldMap.put("key2","5,11,9");

        result = instance.executeWithPlaceHold(map,replaceHoldMap, new ListDataSet<>(data));
        Object row3 = result.get("row3");
        Object row4 = result.get("row4");

        Assert.assertEquals(row1,row3);
        Assert.assertEquals(row2,row4);
    }


    @Test
    public void testExecuteWithExecuteTimeout() {
        SoloscanOptions.set(SoloscanOptions.EXECUTE_TIMEOUT_MS.key(),10);
        SoloscanExecutor instance = SoloscanExecutor.INSTANCE;
        Map<String, String> map = new HashMap<>();
        Map<String, Object> result;
        int count = 10;
        for(int i=0;i<count;i++)
        map.put("row"+i, "{count(SCCC),SCCC,SCCC=5} union {count(SCCC),SCCC,SCCC=11} union {count(SCCC),SCCC,SCCC=9 }");
        try{
            result = instance.execute(map, new ListDataSet<>(data));
            Assert.fail();
        }catch (Exception e){
            Assert.assertTrue(e instanceof ExpressionExecuteException);
            Assert.assertTrue(e.getCause() instanceof TimeoutException);
        }

        SoloscanOptions.set(SoloscanOptions.EXECUTE_TIMEOUT_MS.key(),0);
        result = instance.execute(map, new ListDataSet<>(data));
        Assert.assertEquals(result.size(),count);
    }

    private void assertEqualsAfterRemoveZero(Object inAggFilterPart,Object inFilterPart){
        if(inAggFilterPart instanceof HashMap){
            HashMap<String,Number> aggFilterPart = (HashMap<String,Number>)inAggFilterPart;
            Iterator<String> iterator = aggFilterPart.keySet().iterator();
            while(iterator.hasNext()){
                String key = iterator.next();
                if(Numbers.equiv(aggFilterPart.get(key),0)){
                    iterator.remove();
                }
            }
        }
        Assert.assertEquals(inAggFilterPart,inFilterPart);
    }

}
