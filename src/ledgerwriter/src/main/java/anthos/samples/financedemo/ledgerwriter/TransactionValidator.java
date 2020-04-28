package anthos.samples.financedemo.ledgerwriter;

import org.springframework.stereotype.Component;

@Component
public class TransactionValidator {

    public boolean validateTransaction(String localRoutingNum, String authedAcct, Transaction transaction)
            throws IllegalArgumentException {
        final String fromAcct = transaction.getFromAccountNum();
        final String fromRoute = transaction.getFromRoutingNum();
        final String toAcct = transaction.getToAccountNum();
        final String toRoute = transaction.getToRoutingNum();
        final Integer amount = transaction.getAmount();

        // If this is an internal transaction,
        // ensure it originated from the authenticated user.
        if (fromRoute.equals(localRoutingNum) && !fromAcct.equals(authedAcct)) {
            throw new IllegalArgumentException("sender not authenticated");
        }
        // Validate account and routing numbers.
        if (!LedgerWriterController.ACCT_REGEX.matcher(fromAcct).matches()
                || !LedgerWriterController.ACCT_REGEX.matcher(toAcct).matches()
                || !LedgerWriterController.ROUTE_REGEX.matcher(fromRoute).matches()
                || !LedgerWriterController.ROUTE_REGEX.matcher(toRoute).matches()) {
            throw new IllegalArgumentException("invalid account details");

        }
        // Ensure sender isn't receiver.
        if (fromAcct.equals(toAcct) && fromRoute.equals(toRoute)) {
            throw new IllegalArgumentException("can't send to self");
        }
        // Ensure amount is valid value.
        if (amount <= 0) {
            throw new IllegalArgumentException("invalid amount");
        }
        return true;
    }


}
