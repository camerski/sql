/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package com.amazon.opendistroforelasticsearch.sql.analysis;

import static com.amazon.opendistroforelasticsearch.sql.ast.dsl.AstDSL.aggregate;
import static com.amazon.opendistroforelasticsearch.sql.ast.dsl.AstDSL.alias;
import static com.amazon.opendistroforelasticsearch.sql.ast.dsl.AstDSL.argument;
import static com.amazon.opendistroforelasticsearch.sql.ast.dsl.AstDSL.booleanLiteral;
import static com.amazon.opendistroforelasticsearch.sql.ast.dsl.AstDSL.compare;
import static com.amazon.opendistroforelasticsearch.sql.ast.dsl.AstDSL.field;
import static com.amazon.opendistroforelasticsearch.sql.ast.dsl.AstDSL.filter;
import static com.amazon.opendistroforelasticsearch.sql.ast.dsl.AstDSL.function;
import static com.amazon.opendistroforelasticsearch.sql.ast.dsl.AstDSL.intLiteral;
import static com.amazon.opendistroforelasticsearch.sql.ast.dsl.AstDSL.qualifiedName;
import static com.amazon.opendistroforelasticsearch.sql.ast.dsl.AstDSL.relation;
import static com.amazon.opendistroforelasticsearch.sql.data.model.ExprValueUtils.integerValue;
import static com.amazon.opendistroforelasticsearch.sql.data.type.ExprCoreType.DOUBLE;
import static com.amazon.opendistroforelasticsearch.sql.data.type.ExprCoreType.INTEGER;
import static com.amazon.opendistroforelasticsearch.sql.data.type.ExprCoreType.LONG;
import static com.amazon.opendistroforelasticsearch.sql.data.type.ExprCoreType.STRING;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazon.opendistroforelasticsearch.sql.ast.dsl.AstDSL;
import com.amazon.opendistroforelasticsearch.sql.ast.tree.RareTopN.CommandType;
import com.amazon.opendistroforelasticsearch.sql.ast.tree.UnresolvedPlan;
import com.amazon.opendistroforelasticsearch.sql.exception.SemanticCheckException;
import com.amazon.opendistroforelasticsearch.sql.expression.DSL;
import com.amazon.opendistroforelasticsearch.sql.expression.config.ExpressionConfig;
import com.amazon.opendistroforelasticsearch.sql.planner.logical.LogicalPlan;
import com.amazon.opendistroforelasticsearch.sql.planner.logical.LogicalPlanDSL;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Configuration
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ExpressionConfig.class, AnalyzerTest.class})
class AnalyzerTest extends AnalyzerTestBase {

  @Test
  public void filter_relation() {
    assertAnalyzeEqual(
        LogicalPlanDSL.filter(
            LogicalPlanDSL.relation("schema"),
            dsl.equal(DSL.ref("integer_value", INTEGER), DSL.literal(integerValue(1)))),
        AstDSL.filter(
            AstDSL.relation("schema"),
            AstDSL.equalTo(AstDSL.field("integer_value"), AstDSL.intLiteral(1))));
  }

  @Test
  public void analyze_filter_relation() {
    assertAnalyzeEqual(
        LogicalPlanDSL.filter(
            LogicalPlanDSL.relation("schema"),
            dsl.equal(DSL.ref("integer_value", INTEGER), DSL.literal(integerValue(1)))),
        filter(relation("schema"), compare("=", field("integer_value"), intLiteral(1))));
  }

  @Test
  public void rename_relation() {
    assertAnalyzeEqual(
        LogicalPlanDSL.rename(
            LogicalPlanDSL.relation("schema"),
            ImmutableMap.of(DSL.ref("integer_value", INTEGER), DSL.ref("ivalue", INTEGER))),
        AstDSL.rename(
            AstDSL.relation("schema"),
            AstDSL.map(AstDSL.field("integer_value"), AstDSL.field("ivalue"))));
  }

  @Test
  public void stats_source() {
    assertAnalyzeEqual(
        LogicalPlanDSL.aggregation(
            LogicalPlanDSL.relation("schema"),
            ImmutableList
                .of(DSL.named("avg(integer_value)", dsl.avg(DSL.ref("integer_value", INTEGER)))),
            ImmutableList.of(DSL.named("string_value", DSL.ref("string_value", STRING)))),
        AstDSL.agg(
            AstDSL.relation("schema"),
            AstDSL.exprList(
                AstDSL.alias(
                    "avg(integer_value)",
                    AstDSL.aggregate("avg", field("integer_value")))
            ),
            null,
            ImmutableList.of(
                AstDSL.alias("string_value", field("string_value"))),
            AstDSL.defaultStatsArgs()));
  }

  @Test
  public void rare_source() {
    assertAnalyzeEqual(
        LogicalPlanDSL.rareTopN(
            LogicalPlanDSL.relation("schema"),
            CommandType.RARE,
            10,
            ImmutableList.of(DSL.ref("string_value", STRING)),
            DSL.ref("integer_value", INTEGER)
        ),
        AstDSL.rareTopN(
            AstDSL.relation("schema"),
            CommandType.RARE,
            ImmutableList.of(argument("noOfResults", intLiteral(10))),
            ImmutableList.of(field("string_value")),
            field("integer_value")
        )
    );
  }

  @Test
  public void top_source() {
    assertAnalyzeEqual(
        LogicalPlanDSL.rareTopN(
            LogicalPlanDSL.relation("schema"),
            CommandType.TOP,
            5,
            ImmutableList.of(DSL.ref("string_value", STRING)),
            DSL.ref("integer_value", INTEGER)
        ),
        AstDSL.rareTopN(
            AstDSL.relation("schema"),
            CommandType.TOP,
            ImmutableList.of(argument("noOfResults", intLiteral(5))),
            ImmutableList.of(field("string_value")),
            field("integer_value")
        )
    );
  }

  @Test
  public void rename_to_invalid_expression() {
    SemanticCheckException exception =
        assertThrows(
            SemanticCheckException.class,
            () ->
                analyze(
                    AstDSL.rename(
                        AstDSL.agg(
                            AstDSL.relation("schema"),
                            AstDSL.exprList(
                                AstDSL.alias("avg(integer_value)", AstDSL.aggregate("avg", field(
                                    "integer_value")))),
                            Collections.emptyList(),
                            ImmutableList.of(),
                            AstDSL.defaultStatsArgs()),
                        AstDSL.map(
                            AstDSL.aggregate("avg", field("integer_value")),
                            AstDSL.aggregate("avg", field("integer_value"))))));
    assertEquals(
        "the target expected to be field, but is avg(Field(field=integer_value, fieldArgs=[]))",
        exception.getMessage());
  }

  @Test
  public void project_source() {
    assertAnalyzeEqual(
        LogicalPlanDSL.project(
            LogicalPlanDSL.relation("schema"),
            DSL.named("integer_value", DSL.ref("integer_value", INTEGER)),
            DSL.named("double_value", DSL.ref("double_value", DOUBLE))
        ),
        AstDSL.projectWithArg(
            AstDSL.relation("schema"),
            AstDSL.defaultFieldsArgs(),
            AstDSL.field("integer_value"), // Field not wrapped by Alias
            AstDSL.alias("double_value", AstDSL.field("double_value"))));
  }

  @Test
  public void remove_source() {
    assertAnalyzeEqual(
        LogicalPlanDSL.remove(
            LogicalPlanDSL.relation("schema"), DSL.ref("integer_value", INTEGER), DSL.ref(
                "double_value", DOUBLE)),
        AstDSL.projectWithArg(
            AstDSL.relation("schema"),
            Collections.singletonList(argument("exclude", booleanLiteral(true))),
            AstDSL.field("integer_value"),
            AstDSL.field("double_value")));
  }

  @Disabled("the project/remove command should shrink the type env")
  @Test
  public void project_source_change_type_env() {
    SemanticCheckException exception =
        assertThrows(
            SemanticCheckException.class,
            () ->
                analyze(
                    AstDSL.projectWithArg(
                        AstDSL.projectWithArg(
                            AstDSL.relation("schema"),
                            AstDSL.defaultFieldsArgs(),
                            AstDSL.field("integer_value"),
                            AstDSL.field("double_value")),
                        AstDSL.defaultFieldsArgs(),
                        AstDSL.field("float_value"))));
  }

  @Test
  public void project_values() {
    assertAnalyzeEqual(
        LogicalPlanDSL.project(
            LogicalPlanDSL.values(ImmutableList.of(DSL.literal(123))),
            DSL.named("123", DSL.literal(123)),
            DSL.named("hello", DSL.literal("hello")),
            DSL.named("false", DSL.literal(false))
        ),
        AstDSL.project(
            AstDSL.values(ImmutableList.of(AstDSL.intLiteral(123))),
            AstDSL.alias("123", AstDSL.intLiteral(123)),
            AstDSL.alias("hello", AstDSL.stringLiteral("hello")),
            AstDSL.alias("false", AstDSL.booleanLiteral(false))
        )
    );
  }

  /**
   * SELECT name, AVG(age) FROM test GROUP BY name.
   */
  @Test
  public void sql_group_by_field() {
    assertAnalyzeEqual(
        LogicalPlanDSL.project(
            LogicalPlanDSL.aggregation(
                LogicalPlanDSL.relation("schema"),
                ImmutableList
                    .of(DSL
                        .named("AVG(integer_value)", dsl.avg(DSL.ref("integer_value", INTEGER)))),
                ImmutableList.of(DSL.named("string_value", DSL.ref("string_value", STRING)))),
            DSL.named("string_value", DSL.ref("string_value", STRING)),
            DSL.named("AVG(integer_value)", DSL.ref("AVG(integer_value)", DOUBLE))),
        AstDSL.project(
            AstDSL.agg(
                AstDSL.relation("schema"),
                ImmutableList.of(alias("AVG(integer_value)",
                    aggregate("AVG", qualifiedName("integer_value")))),
                emptyList(),
                ImmutableList.of(alias("string_value", qualifiedName("string_value"))),
                emptyList()),
            AstDSL.alias("string_value", qualifiedName("string_value")),
            AstDSL.alias("AVG(integer_value)", aggregate("AVG", qualifiedName("integer_value"))))
    );
  }

  /**
   * SELECT abs(name), AVG(age) FROM test GROUP BY abs(name).
   */
  @Test
  public void sql_group_by_function() {
    assertAnalyzeEqual(
        LogicalPlanDSL.project(
            LogicalPlanDSL.aggregation(
                LogicalPlanDSL.relation("schema"),
                ImmutableList
                    .of(DSL
                        .named("AVG(integer_value)", dsl.avg(DSL.ref("integer_value", INTEGER)))),
                ImmutableList.of(DSL.named("abs(long_value)",
                    dsl.abs(DSL.ref("long_value", LONG))))),
            DSL.named("abs(long_value)", DSL.ref("abs(long_value)", LONG)),
            DSL.named("AVG(integer_value)", DSL.ref("AVG(integer_value)", DOUBLE))),
        AstDSL.project(
            AstDSL.agg(
                AstDSL.relation("schema"),
                ImmutableList.of(alias("AVG(integer_value)",
                    aggregate("AVG", qualifiedName("integer_value")))),
                emptyList(),
                ImmutableList
                    .of(alias("abs(long_value)", function("abs", qualifiedName("long_value")))),
                emptyList()),
            AstDSL.alias("abs(long_value)", function("abs", qualifiedName("long_value"))),
            AstDSL.alias("AVG(integer_value)", aggregate("AVG", qualifiedName("integer_value"))))
    );
  }
}
