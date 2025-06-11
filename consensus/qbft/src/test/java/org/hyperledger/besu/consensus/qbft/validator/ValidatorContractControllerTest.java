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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ValidatorContractControllerTest {
  private static final String GET_VALIDATORS_FUNCTION_RESULT =
      "00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000eac51e3fe1afc9894f0dfeab8ceb471899b932df";
  private static final Address VALIDATOR_ADDRESS =
      Address.fromHexString("0xeac51e3fe1afc9894f0dfeab8ceb471899b932df");
  private static final Address CONTRACT_ADDRESS = Address.fromHexString("1");
  private static final TransactionValidationParams ALLOW_EXCEEDING_BALANCE_VALIDATION_PARAMS =
      TransactionValidationParams.transactionSimulatorAllowExceedingBalance();
  private static final long TEST_STATE = 42L;
  private static final Bytes TEST_STATE_BYTES =
      Bytes.fromHexString("0x000000000000000000000000000000000000000000000000000000000000002a");

  private TransactionSimulator transactionSimulator;
  private ValidatorContractController validatorContractController;
  private Transaction transaction;

  @BeforeEach
  public void setup() {
    transactionSimulator = Mockito.mock(TransactionSimulator.class);
    validatorContractController = new ValidatorContractController(transactionSimulator);
    transaction = Mockito.mock(Transaction.class);
  }

  @Test
  public void decodesGetValidatorsResultFromContractCall() {
    final TransactionSimulatorResult result =
        new TransactionSimulatorResult(
            transaction,
            TransactionProcessingResult.successful(
                List.of(),
                0,
                0,
                Bytes.fromHexString(GET_VALIDATORS_FUNCTION_RESULT),
                ValidationResult.valid()));

    when(transactionSimulator.process(
            any(CallParameter.class),
            eq(ALLOW_EXCEEDING_BALANCE_VALIDATION_PARAMS),
            eq(OperationTracer.NO_TRACING),
            eq(1L)))
        .thenReturn(Optional.of(result));

    final Collection<Address> validators =
        validatorContractController.getValidators(1, CONTRACT_ADDRESS);
    assertThat(validators).containsExactly(VALIDATOR_ADDRESS);
  }

  @Test
  public void throwErrorIfInvalidSimulationResult() {
    final TransactionSimulatorResult result =
        new TransactionSimulatorResult(
            transaction,
            TransactionProcessingResult.invalid(
                ValidationResult.invalid(TransactionInvalidReason.INTERNAL_ERROR)));

    when(transactionSimulator.process(
            any(CallParameter.class),
            eq(ALLOW_EXCEEDING_BALANCE_VALIDATION_PARAMS),
            eq(OperationTracer.NO_TRACING),
            eq(1L)))
        .thenReturn(Optional.of(result));

    assertThatThrownBy(() -> validatorContractController.getValidators(1, CONTRACT_ADDRESS))
        .hasMessageContaining("Failed validator smart contract call");
  }

  @Test
  public void throwErrorIfFailedSimulationResult() {
    final TransactionSimulatorResult result =
        new TransactionSimulatorResult(
            transaction,
            TransactionProcessingResult.failed(
                0,
                0,
                ValidationResult.invalid(TransactionInvalidReason.INTERNAL_ERROR),
                Optional.empty(),
                Optional.empty()));

    when(transactionSimulator.process(
            any(CallParameter.class),
            eq(ALLOW_EXCEEDING_BALANCE_VALIDATION_PARAMS),
            eq(OperationTracer.NO_TRACING),
            eq(1L)))
        .thenReturn(Optional.of(result));

    assertThatThrownBy(() -> validatorContractController.getValidators(1, CONTRACT_ADDRESS))
        .hasMessageContaining("Failed validator smart contract call");
  }

  @Test
  public void throwErrorIfUnexpectedSuccessfulEmptySimulationResult() {
    final TransactionSimulatorResult result =
        new TransactionSimulatorResult(
            transaction,
            TransactionProcessingResult.successful(
                List.of(), 0, 0, Bytes.EMPTY, ValidationResult.valid()));

    when(transactionSimulator.process(
            any(CallParameter.class),
            eq(ALLOW_EXCEEDING_BALANCE_VALIDATION_PARAMS),
            eq(OperationTracer.NO_TRACING),
            eq(1L)))
        .thenReturn(Optional.of(result));

    assertThatThrownBy(() -> validatorContractController.getValidators(1, CONTRACT_ADDRESS))
        .hasMessage("Unexpected empty result from validator smart contract call");
  }

  @Test
  public void throwErrorIfEmptySimulationResult() {
    when(transactionSimulator.process(
            any(CallParameter.class),
            eq(ALLOW_EXCEEDING_BALANCE_VALIDATION_PARAMS),
            eq(OperationTracer.NO_TRACING),
            eq(1L)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> validatorContractController.getValidators(1, CONTRACT_ADDRESS))
        .hasMessage("Failed validator smart contract call");
  }

  @Test
  public void getParticipantStateReturnsExpectedValue() {
    final TransactionSimulatorResult result =
        new TransactionSimulatorResult(
            transaction,
            TransactionProcessingResult.successful(
                List.of(), 0, 0, TEST_STATE_BYTES, ValidationResult.valid()));

    when(transactionSimulator.process(
            any(CallParameter.class),
            any(TransactionValidationParams.class),
            any(OperationTracer.class),
            anyLong()))
        .thenReturn(Optional.of(result));

    final Long retrievedState = validatorContractController.getParticipantState(1, CONTRACT_ADDRESS);
    assertThat(retrievedState).isEqualTo(TEST_STATE);
  }

  @Test
  public void setParticipantStateSucceeds() {
    final TransactionSimulatorResult result =
        new TransactionSimulatorResult(
            transaction,
            TransactionProcessingResult.successful(
                List.of(), 0, 0, Bytes.EMPTY, ValidationResult.valid()));

    when(transactionSimulator.process(
            any(CallParameter.class),
            any(TransactionValidationParams.class),
            any(OperationTracer.class),
            anyLong()))
        .thenReturn(Optional.of(result));

    assertThatCode(() -> 
        validatorContractController.setParticipantState(1, CONTRACT_ADDRESS, TEST_STATE))
        .doesNotThrowAnyException();
  }

  @Test
  public void setParticipantStateFailsIfContractCallFails() {
    final TransactionSimulatorResult result =
        new TransactionSimulatorResult(
            transaction,
            TransactionProcessingResult.invalid(
                ValidationResult.invalid(TransactionInvalidReason.INTERNAL_ERROR)));

    when(transactionSimulator.process(
            any(CallParameter.class),
            any(TransactionValidationParams.class),
            any(OperationTracer.class),
            anyLong()))
        .thenReturn(Optional.of(result));

    assertThatThrownBy(() -> 
        validatorContractController.setParticipantState(1, CONTRACT_ADDRESS, TEST_STATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(ValidatorContractController.CONTRACT_ERROR_MSG);
  }
}
