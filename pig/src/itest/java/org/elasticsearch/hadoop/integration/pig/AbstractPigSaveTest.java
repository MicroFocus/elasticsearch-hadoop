/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.hadoop.integration.pig;

import java.io.IOException;
import java.util.Date;

import org.apache.hadoop.conf.Configuration;
import org.elasticsearch.hadoop.EsHadoopIllegalStateException;
import org.elasticsearch.hadoop.HdpBootstrap;
import org.elasticsearch.hadoop.Provisioner;
import org.elasticsearch.hadoop.cfg.ConfigurationOptions;
import org.elasticsearch.hadoop.EsAssume;
import org.elasticsearch.hadoop.rest.RestUtils;
import org.elasticsearch.hadoop.rest.RestClient;
import org.elasticsearch.hadoop.util.EsMajorVersion;
import org.elasticsearch.hadoop.util.StringUtils;
import org.elasticsearch.hadoop.util.TestSettings;
import org.elasticsearch.hadoop.util.TestUtils;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static org.elasticsearch.hadoop.util.EsMajorVersion.V_5_X;
import static org.elasticsearch.hadoop.util.TestUtils.docEndpoint;
import static org.elasticsearch.hadoop.util.TestUtils.resource;
import static org.junit.Assert.*;

import static org.hamcrest.CoreMatchers.*;

/**
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AbstractPigSaveTest extends AbstractPigTests {

    private final EsMajorVersion VERSION = TestUtils.getEsClusterInfo().getMajorVersion();
    private final Configuration configuration = HdpBootstrap.hadoopConfig();

    @BeforeClass
    public static void localStartup() throws Exception {
        AbstractPigTests.startup();

        // initialize Pig in local mode
        RestClient client = new RestClient(new TestSettings());
        try {
            client.delete("pig");
        } catch (Exception ex) {
            // ignore
        }
    }

    @Test
    public void testTuple() throws Exception {
        String script =
                "SET mapred.map.tasks 2;" +
                loadArtistSource() +
                //"ILLUSTRATE A;" +
                "B = FOREACH A GENERATE name, TOTUPLE(url, picture) AS links;" +
                "DESCRIBE B;" +
                "ILLUSTRATE B;" +
                "STORE B INTO '"+resource("pig-tupleartists", "data", VERSION)+"' USING org.elasticsearch.hadoop.pig.EsStorage();";
        //"es_total = LOAD 'radio/artists/_count?q=me*' USING org.elasticsearch.hadoop.pig.EsStorage();" +
        //"DUMP es_total;" +
        //"bartists = FILTER B BY name MATCHES 'me.*';" +
        //"allb = GROUP bartists ALL;"+
        //"total = FOREACH allb GENERATE 'total' as foo, COUNT_STAR($1) as total;"+
        //"ILLUSTRATE allb;"+
        //"STORE total INTO '/tmp/total';"+
        //"DUMP total;";
        pig.executeScript(script);
    }

    @Test
    public void testTupleMapping() throws Exception {
        assertThat(RestUtils.getMappings("pig-tupleartists").getResolvedView().toString(),
                VERSION.onOrAfter(V_5_X)
                        ? is("*/*=[links=TEXT, name=TEXT]")
                        : is("*/*=[links=STRING, name=STRING]"));
    }

    @Test
    public void testBag() throws Exception {
        String script =
                loadArtistSource() +
                "B = FOREACH A GENERATE name, TOBAG(url, picture) AS links;" +
                "ILLUSTRATE B;" +
                "STORE B INTO '"+resource("pig-bagartists", "data", VERSION)+"' USING org.elasticsearch.hadoop.pig.EsStorage();";
        pig.executeScript(script);
    }

    @Test
    public void testBagMapping() throws Exception {
        assertThat(RestUtils.getMappings("pig-bagartists").getResolvedView().toString(),
                VERSION.onOrAfter(V_5_X)
                        ? is("*/*=[links=TEXT, name=TEXT]")
                        : is("*/*=[links=STRING, name=STRING]"));
    }

    @Test
    public void testTimestamp() throws Exception {
        long millis = new Date().getTime();
        String script =
                loadArtistSource() +
                "B = FOREACH A GENERATE name, ToDate(" + millis + "l) AS date, url;" +
                "ILLUSTRATE B;" +
                "STORE B INTO '"+resource("pig-timestamp", "data", VERSION)+"' USING org.elasticsearch.hadoop.pig.EsStorage();";

        pig.executeScript(script);
    }

    @Test
    public void testTimestampMapping() throws Exception {
        String mapping = RestUtils.getMappings("pig-timestamp").getResolvedView().toString();
        assertThat(mapping, containsString("date=DATE"));
    }

    @Test
    public void testFieldAlias() throws Exception {
        long millis = new Date().getTime();
        String script =
                loadArtistSource() +
                "B = FOREACH A GENERATE name, ToDate(" + millis + "l) AS timestamp, url, picture;" +
                "ILLUSTRATE B;" +
                "STORE B INTO '"+resource("pig-fieldalias", "data", VERSION)+"' USING org.elasticsearch.hadoop.pig.EsStorage('es.mapping.names=nAme:@name, timestamp:@timestamp, uRL:url, picturE:picture');";

        pig.executeScript(script);
    }

    @Test
    public void testFieldAliasMapping() throws Exception {
        assertThat(RestUtils.getMappings("pig-fieldalias").getResolvedView().toString(),
                VERSION.onOrAfter(V_5_X)
                        ? is("*/*=[@timestamp=DATE, name=TEXT, picture=TEXT, url=TEXT]")
                        : is("*/*=[@timestamp=DATE, name=STRING, picture=STRING, url=STRING]"));
    }

    @Test
    public void testCaseSensitivity() throws Exception {
        String script =
                "A = LOAD '" + PigSuite.testData.sampleArtistsDat(configuration) + "' USING PigStorage() AS (id:long, Name:chararray, uRL:chararray, pIctUre: chararray, timestamp: chararray); " +
                "B = FOREACH A GENERATE Name, uRL, pIctUre;" +
                "ILLUSTRATE B;" +
                "STORE B INTO '"+resource("pig-casesensitivity", "data", VERSION)+"' USING org.elasticsearch.hadoop.pig.EsStorage();";

        pig.executeScript(script);
    }

    @Test
    public void testCaseSensitivityMapping() throws Exception {
        assertThat(RestUtils.getMappings("pig-casesensitivity").getResolvedView().toString(),
                VERSION.onOrAfter(V_5_X)
                        ? is("*/*=[Name=TEXT, pIctUre=TEXT, uRL=TEXT]")
                        : is("*/*=[Name=STRING, pIctUre=STRING, uRL=STRING]"));
    }

    @Test
    public void testEmptyComplexStructures() throws Exception {
        String script =
                loadArtistSource() +
                "AL = LIMIT A 10;" +
                "B = FOREACH AL GENERATE (), [], {};" +
                "STORE B INTO '"+resource("pig-emptyconst", "data", VERSION)+"' USING org.elasticsearch.hadoop.pig.EsStorage();";

        pig.executeScript(script);
    }

    @Test
    public void testEmptyComplexStructuresMapping() throws Exception {
        assertThat(RestUtils.getMappings("pig-emptyconst").getResolvedView().toString(), is("*/*=[]"));
    }

    @Test
    public void testCreateWithId() throws Exception {
        String script =
                loadArtistSource() +
                "B = FOREACH A GENERATE id, name, TOBAG(url, picture) AS links;" +
                "STORE B INTO '"+resource("pig-createwithid", "data", VERSION)+"' USING org.elasticsearch.hadoop.pig.EsStorage('"
                                + ConfigurationOptions.ES_WRITE_OPERATION + "=create','"
                                + ConfigurationOptions.ES_MAPPING_ID + "=id');";
        pig.executeScript(script);
    }

    @Test
    public void testCreateWithIdMapping() throws Exception {
        assertThat(RestUtils.getMappings("pig-createwithid").getResolvedView().toString(),
                VERSION.onOrAfter(V_5_X)
                        ? is("*/*=[id=LONG, links=TEXT, name=TEXT]")
                        : is("*/*=[id=LONG, links=STRING, name=STRING]"));
    }

    @Test(expected = EsHadoopIllegalStateException.class)
    public void testCreateWithIdShouldFailOnDuplicate() throws Exception {
        testCreateWithId();
    }

    @Test(expected = Exception.class)
    public void testUpdateWithoutId() throws Exception {
        String script =
                loadArtistSource() +
                "B = FOREACH A GENERATE id, name, TOBAG(url, picture) AS links;" +
                "STORE B INTO '"+resource("pig-updatewoid", "data", VERSION)+"' USING org.elasticsearch.hadoop.pig.EsStorage('"
                                + ConfigurationOptions.ES_WRITE_OPERATION + "=update');";
        pig.executeScript(script);
    }

    @Test
    public void testUpdateWithId() throws Exception {
        String script =
                loadArtistSource() +
                "B = FOREACH A GENERATE id, name, TOBAG(url, picture) AS links;" +
                "STORE B INTO '"+resource("pig-update", "data", VERSION)+"' USING org.elasticsearch.hadoop.pig.EsStorage('"
                                + ConfigurationOptions.ES_WRITE_OPERATION + "=upsert','"
                                + ConfigurationOptions.ES_MAPPING_ID + "=id');";
        pig.executeScript(script);
    }

    @Test
    public void testUpdateWithIdMapping() throws Exception {
        assertThat(RestUtils.getMappings("pig-update").getResolvedView().toString(),
                VERSION.onOrAfter(V_5_X)
                        ? is("*/*=[id=LONG, links=TEXT, name=TEXT]")
                        : is("*/*=[id=LONG, links=STRING, name=STRING]"));
    }

    @Test(expected = EsHadoopIllegalStateException.class)
    public void testUpdateWithoutUpsert() throws Exception {
        String script =
                loadArtistSource() +
                "B = FOREACH A GENERATE id, name, TOBAG(url, picture) AS links;" +
                "STORE B INTO '"+resource("pig-updatewoupsert", "data", VERSION)+"' USING org.elasticsearch.hadoop.pig.EsStorage('"
                                + ConfigurationOptions.ES_WRITE_OPERATION + "=update','"
                                + ConfigurationOptions.ES_MAPPING_ID + "=id');";
        pig.executeScript(script);
    }

    @Test
    public void testParentChild() throws Exception {
        EsAssume.versionOnOrBefore(EsMajorVersion.V_5_X, "Parent Child Disabled in 6.0");
        RestUtils.createMultiTypeIndex("pig-pc");
        RestUtils.putMapping("pig-pc", "child", "org/elasticsearch/hadoop/integration/mr-child.json");

        String script =
                loadArtistSource() +
                "B = FOREACH A GENERATE id, name, TOBAG(url, picture) AS links;" +
                "STORE B INTO 'pig-pc/child' USING org.elasticsearch.hadoop.pig.EsStorage('"
                                + ConfigurationOptions.ES_MAPPING_PARENT + "=id','"
                                + ConfigurationOptions.ES_INDEX_AUTO_CREATE + "=no');";
        pig.executeScript(script);
    }

    @Test
    public void testParentChildMapping() throws Exception {
        EsAssume.versionOnOrBefore(EsMajorVersion.V_5_X, "Parent Child Disabled in 6.0");
        assertThat(RestUtils.getMapping("pig-pc", "child").toString(),
                VERSION.onOrAfter(V_5_X)
                        ? is("pig-pc/child=[id=LONG, links=TEXT, name=TEXT]")
                        : is("pig-pc/child=[id=LONG, links=STRING, name=STRING]"));
    }

    @Test
    @Ignore("Pig can't really create objects for insertion, so transforming this " +
            "data field into a joiner is bunk right now. Fix this when we figure " +
            "out how to handle tuples well in Pig...")
    public void testJoin() throws Exception {
        RestUtils.putMapping("pig-join", "join", "data/join/mapping/typed.json");
        RestUtils.refresh("pig-join");

        String script =
                "REGISTER " + Provisioner.ESHADOOP_TESTING_JAR + ";" +
                        loadJoinSource()
                        + "CHILDREN = FILTER A BY NOT(parent IS NULL);"
//                        + "CHILDREN = FOREACH A GENERATE id, name, company, TOTUPLE(relation, parent) as joiner;"
                        + "DUMP A;"
                        + "DUMP CHILDREN;"
//                        + "STORE CHILDREN INTO 'pig-join/join' USING org.elasticsearch.hadoop.pig.EsStorage('"
//                        + ConfigurationOptions.ES_MAPPING_JOIN + "=joiner','"
//                        + ConfigurationOptions.ES_INDEX_AUTO_CREATE + "=no');"
                ;
        pig.executeScript(script);
    }

    @Test
    public void testNestedTuple() throws Exception {
        RestUtils.postData(docEndpoint("pig-nestedtuple", "data", VERSION), "{\"my_array\" : [\"1.a\",\"1.b\"]}".getBytes(StringUtils.UTF_8));
        RestUtils.postData(docEndpoint("pig-nestedtuple", "data", VERSION), "{\"my_array\" : [\"2.a\",\"2.b\"]}".getBytes(StringUtils.UTF_8));
        RestUtils.waitForYellow("pig-nestedtuple");
    }


    @Test
    public void testIndexPattern() throws Exception {
        String script =
                loadArtistSource() +
                "STORE A INTO '"+resource("pig-pattern-{tag}", "data", VERSION)+"' USING org.elasticsearch.hadoop.pig.EsStorage();";

        pig.executeScript(script);
    }

    @Test
    public void testIndexPatternMapping() throws Exception {
        assertThat(RestUtils.getMappings("pig-pattern-9").getResolvedView().toString(),
                VERSION.onOrAfter(V_5_X)
                        ? is("*/*=[id=LONG, name=TEXT, picture=TEXT, tag=LONG, timestamp=DATE, url=TEXT]")
                        : is("*/*=[id=LONG, name=STRING, picture=STRING, tag=LONG, timestamp=DATE, url=STRING]"));
    }

    @Test
    public void testIndexPatternFormat() throws Exception {
        String script =
                loadArtistSource() +
                "STORE A INTO '"+resource("pig-pattern-format-{timestamp|YYYY-MM-dd}", "data", VERSION)+"' USING org.elasticsearch.hadoop.pig.EsStorage();";

        pig.executeScript(script);
    }

    @Test
    public void testIndexPatternFormatMapping() throws Exception {
        assertThat(RestUtils.getMappings("pig-pattern-format-2001-10-06").getResolvedView().toString(),
                VERSION.onOrAfter(V_5_X)
                        ? is("*/*=[id=LONG, name=TEXT, picture=TEXT, tag=LONG, timestamp=DATE, url=TEXT]")
                        : is("*/*=[id=LONG, name=STRING, picture=STRING, tag=LONG, timestamp=DATE, url=STRING]"));
    }

    private String loadArtistSource() throws IOException {
        return loadSource(PigSuite.testData.sampleArtistsDat(configuration)) + " AS (id:long, name:chararray, url:chararray, picture: chararray, timestamp: chararray, tag:long);";
    }

    private String loadJoinSource() throws IOException {
        return loadSource(PigSuite.testData.sampleJoinDat(configuration)) + " AS (id:long, company:chararray, name:chararray, relation:chararray, parent:chararray);";
    }

    private String loadSource(String source) {
        return "A = LOAD '" + source + "' USING PigStorage()";
    }
}