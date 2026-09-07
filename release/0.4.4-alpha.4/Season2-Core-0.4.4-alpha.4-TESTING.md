# Season 2 Core 0.4.4-alpha.4 testing

This is an asset/preset integration update. No compiled Java classes changed from 0.4.3.

1. Start the dedicated server and confirm normal startup.
2. Join with the 0.4.4 client JAR.
3. Open/import the Spectral Post Courier preset and confirm the supplied postman skin renders.
4. Confirm the courier preset does not need an external skin URL.
5. Bind the courier through its operator setup dialogue and run `/mail courier status`.
6. Send one letter through a Drop Box and confirm existing courier pathing still behaves as 0.4.3.
7. Deliver to a player with a Letter Box and confirm Letter Box-first routing remains intact.
8. Import/open the Dragon Bank Banker preset and confirm dialogue describes deposits only.
9. Choose account access and confirm `/dragonbank @initiator` opens the working bank.
10. Restart the server and confirm mail/bank data is unchanged.
