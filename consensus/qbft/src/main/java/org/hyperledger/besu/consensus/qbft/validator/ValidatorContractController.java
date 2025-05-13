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
package org.hyperledger.besu.consensus.qbft.validator;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import java.math.BigInteger;
import java.util.Collections;

/** The Validator contract controller. */
public class ValidatorContractController {
  /** The constant GET_VALIDATORS. */
  public static final String GET_VALIDATORS = "getValidators";

  /** The constant GET_NETWORK_STATE. */
  public static final String GET_NETWORK_STATE = "getNetworkState";

  /** The constant GET_PARTICIPANT_STATE. */
  public static final String GET_PARTICIPANT_STATE = "getParticipantState";

  /** The constant SET_PARTICIPANT_STATE. */
  public static final String SET_PARTICIPANT_STATE = "setParticipantState";

  /** The constant CONTRACT_ERROR_MSG. */
  public static final String CONTRACT_ERROR_MSG = "Failed validator smart contract call";

  private final TransactionSimulator transactionSimulator;
  private final Function getValidatorsFunction;
  private final Function getNetworkStateFunction;
  private final Function getParticipantStateFunction;

  /**
   * Instantiates a new Validator contract controller.
   *
   * @param transactionSimulator the transaction simulator
   */
  public ValidatorContractController(final TransactionSimulator transactionSimulator) {
    this.transactionSimulator = transactionSimulator;

    try {
      this.getValidatorsFunction =
          new Function(
              GET_VALIDATORS,
              List.of(),
              List.of(new TypeReference<DynamicArray<org.web3j.abi.datatypes.Address>>() {}));

      this.getNetworkStateFunction =
          new Function(
              GET_NETWORK_STATE,
              List.of(),
              List.of(new TypeReference<org.web3j.abi.datatypes.Uint>() {}));

      this.getParticipantStateFunction =
          new Function(
              GET_PARTICIPANT_STATE,
              List.of(),
              List.of(new TypeReference<org.web3j.abi.datatypes.Uint>() {}));
    } catch (final Exception e) {
      throw new RuntimeException("Error creating smart contract function", e);
    }
  }

  /**
   * Gets validators.
   *
   * @param blockNumber the block number
   * @param contractAddress the contract address
   * @return the validators
   */
  public Collection<Address> getValidators(final long blockNumber, final Address contractAddress) {
    return callFunction(blockNumber, getValidatorsFunction, contractAddress)
        .map(this::parseGetValidatorsResult)
        .orElseThrow(() -> new IllegalStateException(CONTRACT_ERROR_MSG));
  }

  /**
   * Gets network state.
   *
   * @param blockNumber the block number
   * @param contractAddress the contract address
   * @return the network state
   */
  public Long getNetworkState(final long blockNumber, final Address contractAddress) {
    return callFunction(blockNumber, getNetworkStateFunction, contractAddress)
        .map(this::parseGetNetworkStateResult)
        .orElseThrow(() -> new IllegalStateException(CONTRACT_ERROR_MSG));
  }

  /**
   * Gets participant state.
   *
   * @param blockNumber the block number
   * @param contractAddress the contract address
   * @return the participant state
   */
  public Long getParticipantState(final long blockNumber, final Address contractAddress) {
    return callFunction(blockNumber, getParticipantStateFunction, contractAddress)
        .map(this::parseGetParticipantStateResult)
        .orElseThrow(() -> new IllegalStateException(CONTRACT_ERROR_MSG));
  }

  /**
   * Sets participant state.
   *
   * @param blockNumber the block number
   * @param contractAddress the contract address
   * @param state the state to set
   */
  public void setParticipantState(final long blockNumber, final Address contractAddress, final long state) {
    final Function function = new Function(
        SET_PARTICIPANT_STATE,
        Collections.singletonList(new org.web3j.abi.datatypes.generated.Uint256(BigInteger.valueOf(state))),
        List.of());
    var result = callFunction(blockNumber, function, contractAddress)
        .orElseThrow(() -> new IllegalStateException(CONTRACT_ERROR_MSG));
    if (!result.isSuccessful()) {
      throw new IllegalStateException(CONTRACT_ERROR_MSG + ": " + result.getValidationResult());
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Collection<Address> parseGetValidatorsResult(final TransactionSimulatorResult result) {
    final List<Type> resultDecoding = decodeResult(result, getValidatorsFunction);
    final List<org.web3j.abi.datatypes.Address> addresses =
        (List<org.web3j.abi.datatypes.Address>) resultDecoding.get(0).getValue();
    return addresses.stream()
        .map(a -> Address.fromHexString(a.getValue()))
        .collect(Collectors.toList());
  }

  @SuppressWarnings({"rawtypes"})
  private Long parseGetNetworkStateResult(final TransactionSimulatorResult result) {
    final List<Type> resultDecoding = decodeResult(result, getNetworkStateFunction);
    return ((java.math.BigInteger) resultDecoding.get(0).getValue()).longValue();
  }

  @SuppressWarnings({"rawtypes"})
  private Long parseGetParticipantStateResult(final TransactionSimulatorResult result) {
    final List<Type> resultDecoding = decodeResult(result, getParticipantStateFunction);
    return ((java.math.BigInteger) resultDecoding.get(0).getValue()).longValue();
  }

  private Optional<TransactionSimulatorResult> callFunction(
      final long blockNumber, final Function function, final Address contractAddress) {
    final Bytes payload = Bytes.fromHexString(FunctionEncoder.encode(function));
    final CallParameter callParams =
        new CallParameter(null, contractAddress, -1, null, null, payload);
    final TransactionValidationParams transactionValidationParams =
        TransactionValidationParams.transactionSimulatorAllowExceedingBalance();
    return transactionSimulator.process(
        callParams, transactionValidationParams, OperationTracer.NO_TRACING, blockNumber);
  }

  @SuppressWarnings("rawtypes")
  private List<Type> decodeResult(
      final TransactionSimulatorResult result, final Function function) {
    if (result.isSuccessful()) {
      final List<Type> decodedList =
          FunctionReturnDecoder.decode(
              result.result().getOutput().toHexString(), function.getOutputParameters());

      if (decodedList.isEmpty()) {
        throw new IllegalStateException(
            "Unexpected empty result from validator smart contract call");
      }

      return decodedList;
    } else {
      throw new IllegalStateException(
          "Failed validator smart contract call: " + result.getValidationResult());
    }
  }
}
