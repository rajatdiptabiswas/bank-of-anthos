/*
 * Copyright 2020, Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package anthos.samples.financedemo.ledgerwriter;

import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.logging.Logger;
import java.util.regex.Pattern;

@RestController
public final class LedgerWriterController {

    private static final Logger LOGGER =
            Logger.getLogger(LedgerWriterController.class.getName());

    private TransactionRepository transactionRepository;

    private JWTVerifier verifier;

    private String localRoutingNum;
    private String balancesApiUri;
    private String version;
    private TransactionValidator transactionValidator;



    public static final String READINESS_CODE = "ok";
    // account ids should be 10 digits between 0 and 9
    public static final Pattern ACCT_REGEX = Pattern.compile("^[0-9]{10}$");
    // route numbers should be 9 digits between 0 and 9
    public static final Pattern ROUTE_REGEX = Pattern.compile("^[0-9]{9}$");

    /**
     * Constructor.
     *
     * Initializes JWT verifier.
     */

    public LedgerWriterController(
            TransactionRepository transactionRepository,
            JWTVerifier verifier,
            TransactionValidator transactionValidator,
            @Value("${LOCAL_ROUTING_NUM}") String localRoutingNum,
            @Value("http://${BALANCES_API_ADDR}/balances")
                    String balancesApiUri,
            @Value("${VERSION}") String version) {
        this.transactionRepository = transactionRepository;
        this.transactionValidator = transactionValidator;
        this.verifier = verifier;
        this.localRoutingNum = localRoutingNum;
        this.balancesApiUri = balancesApiUri;
        this.version = version;
    }

    /**
     * Version endpoint.
     *
     * @return  service version string
     */
    @GetMapping("/version")
    public ResponseEntity version() {
        return new ResponseEntity<String>(version, HttpStatus.OK);
    }

    /**
     * Readiness probe endpoint.
     *
     * @return HTTP Status 200 if server is ready to receive requests.
     */
    @GetMapping("/ready")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<String> readiness() {
        return new ResponseEntity<String>(READINESS_CODE, HttpStatus.OK);
    }

    /**
     * Submit a new transaction to the ledger.
     *
     * @param bearerToken  HTTP request 'Authorization' header
     * @param transaction  transaction to submit
     *
     * @return  HTTP Status 200 if transaction was successfully submitted
     */
    @PostMapping(value = "/transactions", consumes = "application/json")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<?> addTransaction(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Transaction transaction) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken = bearerToken.split("Bearer ")[1];
        }
        try {
            final DecodedJWT jwt = this.verifier.verify(bearerToken);
            // validate transaction
            transactionValidator.validateTransaction(localRoutingNum, jwt.getClaim("acct").asString(), transaction);

            if (transaction.getFromRoutingNum().equals(localRoutingNum)) {
                checkAvailableBalance(bearerToken, transaction);
            }

            // No exceptions thrown. Add to ledger.
            submitTransaction(transaction);
            return new ResponseEntity<String>("ok", HttpStatus.CREATED);

        } catch (JWTVerificationException e) {
            return new ResponseEntity<String>("not authorized",
                    HttpStatus.UNAUTHORIZED);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return new ResponseEntity<String>(e.toString(),
                    HttpStatus.BAD_REQUEST);
        } catch (ResourceAccessException
                | CannotCreateTransactionException
                | HttpServerErrorException e) {
            return new ResponseEntity<String>(e.toString(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Check there is available funds for this transaction.
     *
     * @param token  the token used to authenticate request
     * @param transaction  the transaction object
     *
     * @throws IllegalStateException     if insufficient funds
     * @throws HttpServerErrorException  if balance service returns 500
     */
    private void checkAvailableBalance(String token, Transaction transaction)
            throws IllegalStateException, HttpServerErrorException {
        final String fromAcct = transaction.getFromAccountNum();
        final Integer amount = transaction.getAmount();

        // Ensure sender balance can cover transaction.
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity entity = new HttpEntity(headers);
        RestTemplate restTemplate = new RestTemplate();
        String uri = balancesApiUri + "/" + fromAcct;
        ResponseEntity<Integer> response = restTemplate.exchange(
                uri, HttpMethod.GET, entity, Integer.class);
        Integer senderBalance = response.getBody();
        if (senderBalance < amount) {
            throw new IllegalStateException("insufficient balance");
        }
    }

    private void submitTransaction(Transaction transaction) {
        LOGGER.fine("Submitting transaction " + transaction.toString());
        transactionRepository.save(transaction);
    }
}