As Java Developer:
- In /query endpoint add in request body score threshold, to add posibillity to tune it by caller. It will be optional field. Set to 0 should return all topK results.
- If score is not passed use default score value. Taken from configuration.
