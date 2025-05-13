/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
pragma solidity >= 0.5.3;

// ... existing code ...
contract Validators {
    address[] validators;
    uint256 private networkState;
    // Add new private mutable variable
    uint256 private participantState;

    constructor() {}

    function getValidators() public view returns (address[] memory) {
        return validators;
    }

    function getNetworkState() public view returns (uint256) {
        return networkState;
    }

    function setNetworkState(uint256 _state) public {
        networkState = _state;
    }

    // Add new getter and setter for participantState
    function getParticipantState() public view returns (uint256) {
        return participantState;
    }

    function setParticipantState(uint256 _state) public {
        participantState = _state;
    }
}
