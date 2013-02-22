package com.continuuity.internal.app.runtime.service;

import com.continuuity.app.program.Program;
import com.continuuity.app.program.Type;
import com.continuuity.app.runtime.ProgramOptions;
import com.continuuity.app.runtime.ProgramRunner;
import com.continuuity.app.runtime.ProgramRuntimeService;
import com.continuuity.app.runtime.RunId;
import com.continuuity.internal.app.runtime.ProgramRunnerFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;

import java.util.Map;

/**
 *
 */
public final class InMemoryProgramRuntimeService extends AbstractIdleService implements ProgramRuntimeService {

  private final ProgramRunnerFactory programRunnerFactory;
  private final Table<Type, RunId, RuntimeInfo> runtimeInfos;

  @Inject
  public InMemoryProgramRuntimeService(ProgramRunnerFactory programRunnerFactory) {
    this.programRunnerFactory = programRunnerFactory;
    this.runtimeInfos = HashBasedTable.create();
  }

  @Override
  public RuntimeInfo run(Program program, ProgramOptions options) {
    ProgramRunner runner = null;
    switch (program.getProcessorType()) {
      case FLOW:
        runner = programRunnerFactory.create(ProgramRunnerFactory.Type.FLOW);
        break;
      case PROCEDURE:
        runner = programRunnerFactory.create(ProgramRunnerFactory.Type.PROCEDURE);
        break;
      case BATCH:
        runner = programRunnerFactory.create(ProgramRunnerFactory.Type.BATCH);
        break;
    }
    Preconditions.checkNotNull(runner, "Fail to get ProgramRunner for type " + program.getProcessorType());

    RuntimeInfo info = new SimpleRuntimeInfo(runner.run(program, options), program);
    runtimeInfos.put(info.getType(), info.getController().getRunId(), info);
    return info;
  }

  @Override
  public RuntimeInfo lookup(RunId runId) {
    Map<Type, RuntimeInfo> column = runtimeInfos.column(runId);
    if (column.size() != 1) {
      // It should be exactly one if the the program is running.
      return null;
    }
    return column.values().iterator().next();
  }

  @Override
  public Map<RunId, RuntimeInfo> list(Type type) {
    return ImmutableMap.copyOf(runtimeInfos.row(type));
  }

  @Override
  protected void startUp() throws Exception {
    // No-op
  }

  @Override
  protected void shutDown() throws Exception {
    // No-op
  }
}
