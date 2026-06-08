# Requirements for Gashapaw App

## High Level User Flow
1) Display "Press button to pay for PAWB PCB" on Display
2) Listen for a button press to enable Payment request
3) When a user presses the button, display, "Use Square Reader to pay $15 (plus tax) for a PAWB PCB"
4) Use the Square SDK to create a payment request on the attached square reader
5) When the user taps their device/card and the transaction completes, the device triggers a solenoid to release the gashapaw mechanism
6) Display says "Thank you for our purchase, turn the handle to receive your paw-ize"
7) After 10 sec, move back to 1)


## Implementation Considerations
- App is not designed to be shown to the user and should only be shown to pawtendees.
- App needs to launch when the device boots and stay awake
  - App uses a ForegroundService, only need to launch the app when the device boots automatically.
- At any point, if there is an issue, the Display needs to default to "Sowwy, there was a problem, notify the paw-tendant"
- At step 4), if the user doesn't tap in 30 sec, return to step 1.
- At step 5), we need to handle transaction failed responses and notify the user of the reason, "Sorry, that didn't work!" and go to step 1.
