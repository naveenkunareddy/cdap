/*
 * Copyright © 2016 Cask Data, Inc.
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

package co.cask.cdap.data2.util.hbase;

import co.cask.cdap.common.namespace.NamespaceQueryAdmin;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.NamespaceConfig;
import co.cask.cdap.proto.NamespaceMeta;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link NamespaceQueryAdmin} which simply returns a {@link NamespaceMeta} with
 * a {@link NamespaceConfig} that contains a custom hbase mapping if it is created with one in its constructor.
 */
public class SimpleNamespaceQueryAdmin implements NamespaceQueryAdmin {
  private final Map<String, String> cdapToHBaseNamespaceMap;

  public SimpleNamespaceQueryAdmin() {
    this.cdapToHBaseNamespaceMap = ImmutableMap.of();
  }

  public SimpleNamespaceQueryAdmin(Map<String, String> cdapToHBaseNamespaceMap) {
    this.cdapToHBaseNamespaceMap = ImmutableMap.copyOf(cdapToHBaseNamespaceMap);
  }

  @Override
  public List<NamespaceMeta> list() throws Exception {
    throw new UnsupportedOperationException("Listing of namespaces is not supported.");
  }

  @Override
  public NamespaceMeta get(Id.Namespace namespaceId) throws Exception {
    String hbaseNamespace = cdapToHBaseNamespaceMap.containsKey(namespaceId.getId()) ?
      cdapToHBaseNamespaceMap.get(namespaceId.getId()) : "";
    return new NamespaceMeta.Builder().setName(namespaceId.getId()).setHBaseDatabase(hbaseNamespace).build();
  }

  @Override
  public boolean exists(Id.Namespace namespaceId) throws Exception {
    throw new UnsupportedOperationException("Check of namespace existence is not supported.");
  }
}
