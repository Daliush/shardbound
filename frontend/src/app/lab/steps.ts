/** A pipeline's steps as the lab shows them: their name, what each does in a few words, and its icon. */
export const STEP_NAMES: Record<string, { name: string; role: string; icon: string }> = {
  retriever: {name:'Retriever',role:'Ranks every chunk',icon:'search'},
  expand: {name:'Expansion',role:'Adds what is linked',icon:'link'},
  rerank: {name:'Reranker',role:'Reorders closely',icon:'rerank'},
  context: {name:'Context',role:'Fills the prompt',icon:'scroll'},
};
