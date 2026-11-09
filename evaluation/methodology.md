# Evaluation methodology

## What is measured

The evaluation answers two questions about **retrieval**, the part of RAG that decides whether a correct answer is even possible:

1. **Ranking**: for a question whose answer is in the notes, is the correct document and page among the passages returned?
2. **Rejection**: for a question whose answer is not in the notes, does the relevance gate return nothing, so the student sees "not found" rather than a guess?

`results.md` covers retrieval only. Answers from a real model are measured separately; see "Evaluating generation" below.

## How it runs

`backend/src/test/java/com/notemind/evaluation/RetrievalEvaluationIT.java` uses the production code path:

| Step | Component |
|---|---|
| Build PDFs from `corpus/*.txt` | PDFBox (test helper) |
| Extract, clean, chunk | `PdfTextExtractor`, `HeaderFooterCleaner`, `TextChunker` |
| Embed | real local model, all-MiniLM-L6-v2, through Spring AI |
| Store and search | `ChunkRepository` SQL on a real pgvector container |

It writes `results.md`. The generated PDFs in `corpus/` also work as demo material.

```bash
cd backend
./mvnw verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RetrievalEvaluationIT -Dnotemind.eval=true
```

## Metrics

- **Hit@k**: share of answerable questions where the expected document and page appear in the top k passages.
- **MRR**: mean of 1/rank of the first correct passage.
- **Correct page kept**: expected page is still in the top 5 after the similarity threshold is applied.
- **Unanswerable rejected**: share of unanswerable questions where no passage clears the threshold.

## Dataset

`questions.json` has 25 answerable questions with expected answers and expected document and page, plus 8 unanswerable questions. Questions are paraphrased rather than copied from the notes.

## Limitations

- The corpus is small, clean, and was written by the same author as the questions. Expect lower numbers on real slides with diagrams, sparse bullets, and extraction noise.
- Most pages fit in one chunk, so this says little about splitting within long pages.
- The threshold of 0.30 was picked from this same data, so its result is optimistic. It should be re-checked on real course material.
- Unanswerable questions are mostly far from the corpus topics. Near-topic questions are harder and are only lightly covered (N07, N08).
- The threshold is specific to the embedding model. Changing models means re-running this evaluation.

## Evaluating generation

`run_generation_eval.py` tests the running application through its public API with whichever chat model is configured, and writes `generation_results.md`:

```bash
python evaluation/run_generation_eval.py llama3.2
```

It measures:

- **Key term**: the answer contains a term from `generation_questions.json`. This is a coarse automatic check, not a judgement of the whole answer.
- **Citation**: the expected document and page are among the cited sources.
- **Abstention**: off-topic questions, and near-topic questions whose specific fact is absent from the notes.
- **Bangla**: the expected page is retrieved, and the answer is in Bangla.
- **Follow-ups**: a context-dependent question retrieves the expected page.
- **Prompt injection**: an instruction embedded in an uploaded PDF is not obeyed.

Results depend heavily on the model. Re-run the script after changing models, and do not reuse these scores for a different one.
