/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package co.cask.cdap.internal.app.services;

import co.cask.cdap.WordCountApp;
import co.cask.cdap.app.runtime.ProgramController;
import co.cask.cdap.app.runtime.ProgramRuntimeService;
import co.cask.cdap.app.runtime.ProgramRuntimeService.RuntimeInfo;
import co.cask.cdap.app.store.Store;
import co.cask.cdap.common.app.RunIds;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.utils.Tasks;
import co.cask.cdap.internal.app.services.http.AppFabricTestBase;
import co.cask.cdap.internal.app.store.DefaultStore;
import co.cask.cdap.internal.app.store.RunRecordMeta;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.ProgramRunStatus;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.RunRecord;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.apache.http.HttpResponse;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;


/**
 * Unit test for {@link ProgramLifecycleService}
 */
public class ProgramLifecycleServiceTest extends AppFabricTestBase {

  private static final Map<String, String> EMPTY_STRING_MAP = ImmutableMap.of();
  private static ProgramLifecycleService programLifecycleService;
  private static Store store;
  private static ProgramRuntimeService runtimeService;

  @BeforeClass
  public static void setup() throws Exception {
    programLifecycleService = getInjector().getInstance(ProgramLifecycleService.class);
    store = getInjector().getInstance(DefaultStore.class);
    runtimeService = getInjector().getInstance(ProgramRuntimeService.class);
  }

  @Test
  public void testInvalidFlowRunRecord() throws Exception {
    final Id.Program wordcountFlow1 =
      Id.Program.from(TEST_NAMESPACE1, "WordCountApp", ProgramType.FLOW, "WordCountFlow");
    String pid = deployProgramAndInvalidate(wordcountFlow1);
    int failureRuns = getProgramRuns(wordcountFlow1, ProgramRunStatus.FAILED.toString()).size();

    // Use the store to manipulate state to be RUNNING
    long nowSecs = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    store.setStartAndRun(wordcountFlow1.toEntityId(), pid, nowSecs, nowSecs + 1);

    // Now check again via Store to assume data store is wrong.
    RunRecord runRecordMeta = store.getRun(wordcountFlow1.toEntityId(), pid);
    Assert.assertNotNull(runRecordMeta);
    Assert.assertEquals(ProgramRunStatus.RUNNING, runRecordMeta.getStatus());

    // Verify there are no new failed run record for the application
    List<RunRecord> runRecords = getProgramRuns(wordcountFlow1, ProgramRunStatus.FAILED.toString());
    Assert.assertEquals(failureRuns, runRecords.size());

    // Lets fix it
    Set<String> processedInvalidRunRecordIds = Sets.newHashSet();
    programLifecycleService.validateAndCorrectRunningRunRecords(ProgramType.FLOW, processedInvalidRunRecordIds);

    // Verify there is one FAILED run record for the application
    runRecords = getProgramRuns(wordcountFlow1, ProgramRunStatus.FAILED.toString());
    Assert.assertEquals(failureRuns + 1, runRecords.size());
    Assert.assertEquals(ProgramRunStatus.FAILED, runRecords.get(0).getStatus());
  }

  @Test
  public void testInvalidStartFlowTimeout() throws Exception {
    // Create App with Flow and the deploy
    final Id.Program wordcountFlow1 =
      Id.Program.from(TEST_NAMESPACE1, "WordCountApp", ProgramType.FLOW, "WordCountFlow");
    String pid = deployProgramAndInvalidate(wordcountFlow1);
    int failureRuns = getProgramRuns(wordcountFlow1, ProgramRunStatus.FAILED.toString()).size();

    // Use the store to manipulate starting state to be five minutes ago
    long fiveMinutesAgo = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
                          - TimeUnit.MINUTES.toSeconds(5);

    store.setStart(wordcountFlow1.toEntityId(), pid, fiveMinutesAgo, null, EMPTY_STRING_MAP, EMPTY_STRING_MAP);

    // Now check again via Store to assume data store is wrong.
    RunRecord runRecordMeta = store.getRun(wordcountFlow1.toEntityId(), pid);
    Assert.assertNotNull(runRecordMeta);
    Assert.assertEquals(ProgramRunStatus.STARTING, runRecordMeta.getStatus());

    // Verify there are no new failed run records for the application
    List<RunRecord> runRecords = getProgramRuns(wordcountFlow1, ProgramRunStatus.FAILED.toString());
    Assert.assertEquals(failureRuns, runRecords.size());

    // Lets fix it
    Set<String> processedInvalidRunRecordIds = Sets.newHashSet();
    programLifecycleService.validateAndCorrectRunningRunRecords(ProgramType.FLOW, processedInvalidRunRecordIds);

    // Verify there is one FAILED run record for the application
    runRecords = getProgramRuns(wordcountFlow1, ProgramRunStatus.FAILED.toString());
    Assert.assertEquals(failureRuns + 1, runRecords.size());
    Assert.assertEquals(ProgramRunStatus.FAILED, runRecords.get(0).getStatus());
  }

  private String deployProgramAndInvalidate(final Id.Program wordcountFlow1) throws Exception {
    HttpResponse response = deploy(WordCountApp.class, Constants.Gateway.API_VERSION_3_TOKEN, TEST_NAMESPACE1);
    Assert.assertEquals(200, response.getStatusLine().getStatusCode());

    // flow is stopped initially
    Assert.assertEquals("STOPPED", getProgramStatus(wordcountFlow1));

    // start a flow and check the status
    startProgram(wordcountFlow1);
    verifyProgramRuns(wordcountFlow1, ProgramRunStatus.RUNNING.toString());

    // Get the RunRecord
    List<RunRecord> runRecords = getProgramRuns(wordcountFlow1, ProgramRunStatus.RUNNING.toString());
    Assert.assertEquals(1, runRecords.size());
    final RunRecord rr = runRecords.get(0);

    // Check the RunRecords status
    Assert.assertEquals(ProgramRunStatus.RUNNING, rr.getStatus());

    // Lets set the runtime info to off
    RuntimeInfo runtimeInfo = runtimeService.lookup(wordcountFlow1.toEntityId(), RunIds.fromString(rr.getPid()));
    ProgramController programController = runtimeInfo.getController();
    programController.stop();

    // Verify that the status of that run is KILLED
    Tasks.waitFor(ProgramRunStatus.KILLED, new Callable<ProgramRunStatus>() {
      @Override
      public ProgramRunStatus call() throws Exception {
        RunRecordMeta runRecord = store.getRun(wordcountFlow1.toEntityId(), rr.getPid());
        return runRecord == null ? null : runRecord.getStatus();
      }
    }, 5, TimeUnit.SECONDS, 100, TimeUnit.MILLISECONDS);

    return rr.getPid();
  }
}
