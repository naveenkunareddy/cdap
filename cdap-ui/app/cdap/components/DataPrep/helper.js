/*
 * Copyright © 2017 Cask Data, Inc.
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

import DataPrepStore from 'components/DataPrep/store';
import {MyArtifactApi} from 'api/artifact';
import MyDataPrepApi from 'api/dataprep';
import {findHighestVersion} from 'services/VersionRange/VersionUtilities';
import Version from 'services/VersionRange/Version';
import DataPrepActions from 'components/DataPrep/store/DataPrepActions';
import NamespaceStore from 'services/NamespaceStore';

export function directiveRequestBodyCreator(directivesArray, wsId) {
  let workspaceId = wsId || DataPrepStore.getState().dataprep.workspaceId;

  return {
    version: 1.0,
    workspace: {
      name: workspaceId,
      results: 100
    },
    recipe: {
      directives: directivesArray
    },
    sampling: {
      method: "FIRST",
      limit: 1000
    }
  };
}

export function isCustomOption(selectedOption) {
  return selectedOption.substr(0, 6) === 'CUSTOM';
}

export function setPopoverOffset(element, footerHeight = 54) {
  let elem = element;
  let elemBounding = elem.getBoundingClientRect();
  const FOOTER_HEIGHT = footerHeight;

  let popover = document.getElementsByClassName('second-level-popover');
  let popoverHeight = popover[0].getBoundingClientRect().height;
  let tableContainerScroll = document.getElementById('dataprep-table-id').scrollTop;
  let popoverMenuItemTop = elemBounding.top;
  let bodyBottom = document.body.getBoundingClientRect().bottom - FOOTER_HEIGHT;
  let bodyTop = document.body.getBoundingClientRect().top;

  let diff = bodyBottom - (popoverMenuItemTop + popoverHeight) - tableContainerScroll;

  if (diff < 0) {
    // this is to make sure the top doesn't go off screen
    if (diff < -popoverMenuItemTop + bodyTop) {
      diff = -popoverMenuItemTop + bodyTop + 10; // pad 10px at the top so that popover isn't stuck at very top
    }
    popover[0].style.top = `${diff}px`;
    // This is to align the bottom of second level popover menu with that of the main menu
    if (elemBounding.bottom > popover[0].getBoundingClientRect().bottom) {
      popover[0].style.bottom = `-1px`;
      popover[0].style.top = 'inherit';
    }
  } else {
    popover[0].style.top = 0;
  }
}

export function checkDataPrepHigherVersion() {
  let namespace = NamespaceStore.getState().selectedNamespace;

  // Check artifacts upgrade
  MyArtifactApi.list({ namespace })
    .combineLatest(MyDataPrepApi.getApp({ namespace }))
    .subscribe((res) => {
      let wranglerArtifactVersions = res[0].filter((artifact) => {
        return artifact.name === 'wrangler-service';
      }).map((artifact) => {
        return artifact.version;
      });

      let highestVersion = findHighestVersion(wranglerArtifactVersions);
      let currentAppArtifactVersion = new Version(res[1].artifact.version);

      if (highestVersion.compareTo(currentAppArtifactVersion) === 1) {
        DataPrepStore.dispatch({
          type: DataPrepActions.setHigherVersion,
          payload: {
            higherVersion: highestVersion.toString()
          }
        });
      }
    });
}

export function columnNameAlreadyExists(colName) {
  let headers = DataPrepStore.getState().dataprep.headers;
  return headers.indexOf(colName) !== -1;
}
