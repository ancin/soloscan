package org.soloquest.soloscan.compiler;

import lombok.extern.slf4j.Slf4j;
import org.soloquest.soloscan.*;
import org.soloquest.soloscan.compiler.codegen.*;
import org.soloquest.soloscan.compiler.lexer.SoloscanLexer;
import org.soloquest.soloscan.compiler.parser.AggFunctionText;
import org.soloquest.soloscan.compiler.parser.SoloscanParser;
import org.soloquest.soloscan.exception.ExpressionCompileException;
import org.soloquest.soloscan.exception.ExpressionRuntimeException;
import org.soloquest.soloscan.runtime.aggfunction.AggFunction;
import org.soloquest.soloscan.runtime.aggfunction.AggInner;
import org.soloquest.soloscan.utils.MiscUtils;
import org.soloquest.soloscan.utils.Preconditions;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class SoloscanCompiler {

    private final SoloscanExecutor instance;
    private final SoloscanClassloader classLoader;
    private final Map<String, String> expressionStringMap = new HashMap<>();
    private SoloscanCache<AggFunctionText,AggInner> aggUnitCache = new SoloscanCache<>(false, (text) -> {
        return genAggInner(text);
    });

    private LinkedList<MetricUnitExpression> metricUnitExpressions = new LinkedList<>();
    private LinkedList<AggFunctionUnit> aggFunctionUnits = new LinkedList<>();

    public SoloscanCompiler(SoloscanExecutor instance, SoloscanClassloader classLoader, Map<String, String> expressionStringMap) {
        this.instance = instance;
        this.classLoader = classLoader;
        this.expressionStringMap.putAll(expressionStringMap);
    }

    public Map<String, Expression> compile() {
        return expressionStringMap.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> {
            return compile(e.getValue());
        }));
    }


    public Expression compile(String expressionString) {
        Preconditions.checkNotNullOrEmpty(expressionString, "Blank expression");
        SoloscanLexer lexer = new SoloscanLexer(instance, expressionString);
        SoloExpressionRealCodeGenerator<BaseSoloExpression> realCodeGenerator = new SoloExpressionRealCodeGenerator(instance, classLoader, Expression.class);
        CodeGeneratorProxy codeGenerator = new CodeGeneratorProxy(instance, classLoader, realCodeGenerator);
        SoloscanParser<BaseSoloExpression> parser = new SoloscanParser(this,instance, lexer, codeGenerator);
        codeGenerator.setParser(parser);
        BaseSoloExpression baseSoloExpression = parser.parseSoloExpression();
        Preconditions.checkArgument(aggFunctionUnits.size()==0, "AggFunctionUnit must be empty");
        while(true){
            MetricUnitExpression metricUnitExpression = metricUnitExpressions.pollFirst();
            if(metricUnitExpression == null){
                break;
            }
            baseSoloExpression.addMetricUnit(metricUnitExpression);
        }
        return baseSoloExpression;
    }

    public Expression compileMetricUnit(String expressionString) {
        Preconditions.checkNotNullOrEmpty(expressionString, "Blank expression");
        SoloscanLexer lexer = new SoloscanLexer(instance, expressionString);
        MetricUnitRealCodeGenerator<BaseMetricUnitExpression> realCodeGenerator = new MetricUnitRealCodeGenerator(instance, classLoader, Expression.class);
        CodeGeneratorProxy codeGenerator = new CodeGeneratorProxy(instance, classLoader, realCodeGenerator);
        SoloscanParser<BaseMetricUnitExpression> parser = new SoloscanParser(this,instance, lexer, codeGenerator);
        codeGenerator.setParser(parser);
        BaseMetricUnitExpression metricUnitExpression = parser.parseMetricUnitExpression();
        while(true){
            AggFunctionUnit aggFunctionUnit = aggFunctionUnits.pollFirst();
            if(aggFunctionUnit == null){
                break;
            }
            metricUnitExpression.addAggFunctionUnit(aggFunctionUnit);
        }
        metricUnitExpressions.add(metricUnitExpression);
        return metricUnitExpression;
    }
    public AggFunctionUnit compileAggFunctionUnit(AggFunctionText aggFunctionText) {
        try {
            AggFunctionUnit aggFunctionUnit;
            Function<AggFunctionText, ? extends AggFunction> function = instance.getAggFunction(aggFunctionText.getName());
            if(function == null){
                throw new ExpressionCompileException("AggFunction " + aggFunctionText.getName() + " not found");
            }
            if (MiscUtils.isBlank(aggFunctionText.getInnerString())) {
                aggFunctionUnit = AggFunctionUnit.newAggFunctionUnit(aggFunctionText, function, null);
            } else {
                aggFunctionUnit = AggFunctionUnit.newAggFunctionUnit(aggFunctionText, function, aggUnitCache.getR(aggFunctionText));
            }
            aggFunctionUnits.add(aggFunctionUnit);
            return aggFunctionUnit;
        }catch (Exception e) {
            if (e instanceof ExecutionException) {
                if (e.getCause() instanceof ExpressionRuntimeException) {
                    throw (ExpressionRuntimeException) e.getCause();
                }
            }
            throw new ExpressionCompileException(e);
        }
    }

    private AggInner genAggInner(AggFunctionText aggFunctionText) {
        String innerString = aggFunctionText.getInnerString();
        if (!MiscUtils.isBlank(innerString)) {

                    SoloscanLexer lexer = new SoloscanLexer(instance, innerString);
                AggInnerRealCodeGenerator<AggInner> realCodeGenerator = new AggInnerRealCodeGenerator(instance, classLoader, AggInner.class);
        CodeGeneratorProxy codeGenerator = new CodeGeneratorProxy(instance, classLoader, realCodeGenerator);
        SoloscanParser<AggInner> parser = new SoloscanParser(this,instance, lexer, codeGenerator);
        codeGenerator.setParser(parser);


            AggInner aggInner = null;
            if (isXAggFunction(aggFunctionText)) {
                aggInner = parser.parseXAggFunctionInner();
            } else {
                aggInner = parser.parseAggFunctionInner();
            }
            return aggInner;
        }
        return null;
    }

    private boolean isXAggFunction(AggFunctionText aggFunctionText) {
        return !aggFunctionText.getName().equals("max") && (aggFunctionText.getName().endsWith("X") || aggFunctionText.getName().endsWith("x"));
    }

}
