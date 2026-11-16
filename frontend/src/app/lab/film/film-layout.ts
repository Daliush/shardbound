/** A rectangle of the film's stage, in stage units: the stage is drawn at a fixed size, then scaled to fit. */
export interface Box { x: number; y: number; w: number; h: number; }

/**
 * Where everything sits on the stage. A wide stage puts the question and the steps on the left, the ranking in the
 * middle and the prompt on the right; a tall one, for a phone, stacks them.
 */
export interface StageLayout {
  tall: boolean; width: number; height: number;
  question: Box; steps: Box[]; bench: Box; prompt: Box; result: Box;
  /** The line under the bench's name that counts where the needed chunks are. */
  tally: Box;
  /** The cut under the 10th rank: what is above it reaches the prompt. */
  cut: number;
  archive(index: number): Box;
  /**
   * A rank of a ranking of `total` chunks: the first 10 as readable rows, the others as tiles under the cut, large
   * enough to carry their rank when there are few of them.
   */
  rank(rank: number, total: number): Box;
  /** Where the number of each of the first 10 ranks is written, left of its row. */
  rankNumber(rank: number): Box;
  /** The tray at the foot of the ranking where a needed chunk a step cut waits, with its label. */
  tray(index: number): Box;
  trayLabel: Box;
  slot(index: number, count: number, area?: Box): Box;
  sides: [Box, Box];
  metric(index: number): Box;
}

/** Past this many tiles under the cut, they are too small to carry a number. */
export const NUMBERED_TILES = 40;
const GUTTER = 24;

export function stageLayout(tall: boolean): StageLayout { return tall ? tallLayout() : wideLayout(); }

function wideLayout(): StageLayout {
  const bench = {x:328,y:32,w:340,h:476}, prompt = {x:700,y:32,w:228,h:476};
  const rowTop = bench.y + 54, rowPitch = 28, rowHeight = 24, cut = rowTop + 10*rowPitch + 14, tileTop = cut + 10, trayTop = 486;
  return {
    tall:false, width:960, height:540,
    question:{x:32,y:32,w:264,h:164},
    steps:[0,1,2,3].map(index => ({x:32,y:220 + index*72,w:264,h:60})),
    bench, prompt, cut,
    tally:{x:bench.x,y:bench.y + 20,w:bench.w,h:20},
    result:{x:prompt.x,y:350,w:prompt.w,h:150},
    archive:grid(bench,rowTop,{columns:14,w:20,h:15,x:24,y:20},14),
    rank:(rank,total) => rank <= 10 ? {x:bench.x + GUTTER,y:rowTop + (rank-1)*rowPitch,w:bench.w - GUTTER,h:rowHeight} : tile(bench,tileTop,rank,total,24),
    rankNumber:rank => ({x:bench.x,y:rowTop + (rank-1)*rowPitch,w:GUTTER - 6,h:rowHeight}),
    tray:index => ({x:bench.x + 44 + index*72,y:trayTop,w:66,h:20}),
    trayLabel:{x:bench.x,y:trayTop,w:40,h:20},
    slot:(index,count,area = prompt) => { const columns = count > 16 ? 3 : 2; return slotIn(area,index,columns,58,columns > 2 ? 21 : 25,count > 18 ? 3 : 5); },
    sides:[{x:328,y:32,w:288,h:476},{x:640,y:32,w:288,h:476}],
    metric:index => ({x:prompt.x,y:prompt.y + index*119,w:prompt.w,h:109}),
  };
}

/** A phone's stage: everything stacked, packed tight, so that it fits the screen above the caption. */
function tallLayout(): StageLayout {
  const bench = {x:16,y:166,w:368,h:400}, prompt = {x:16,y:576,w:368,h:142};
  const rowTop = bench.y + 42, rowPitch = 23, rowHeight = 20, cut = rowTop + 10*rowPitch + 16, tileTop = cut + 8, trayTop = tileTop + 84;
  return {
    tall:true, width:400, height:732,
    question:{x:16,y:12,w:368,h:100},
    steps:[0,1,2,3].map(index => ({x:16 + index*94,y:120,w:86,h:38})),
    bench, prompt, cut,
    tally:{x:bench.x,y:bench.y + 18,w:bench.w,h:18},
    result:{x:bench.x,y:bench.y + 42,w:bench.w,h:200},
    archive:grid(bench,rowTop,{columns:16,w:18,h:13,x:22,y:17},12),
    rank:(rank,total) => rank <= 10 ? {x:bench.x + GUTTER,y:rowTop + (rank-1)*rowPitch,w:bench.w - GUTTER,h:rowHeight} : tile(bench,tileTop,rank,total,26),
    rankNumber:rank => ({x:bench.x,y:rowTop + (rank-1)*rowPitch,w:GUTTER - 6,h:rowHeight}),
    tray:index => ({x:bench.x + 44 + index*72,y:trayTop,w:66,h:18}),
    trayLabel:{x:bench.x,y:trayTop,w:40,h:18},
    slot:(index,_count,area = prompt) => slotIn(area,index,3,30,20,3),
    sides:[{x:16,y:166,w:368,h:262},{x:16,y:442,w:368,h:276}],
    metric:index => ({x:prompt.x + (index%2)*188,y:prompt.y + Math.floor(index/2)*76 - 4,w:180,h:70}),
  };
}

/** The archive: every chunk a cell of a grid centred under the bench's head. */
function grid(bench: Box, top: number, cell: {columns: number; w: number; h: number; x: number; y: number}, rows: number): (index: number) => Box {
  const width = cell.columns*cell.x - (cell.x - cell.w), first = top + (bench.y + bench.h - 30 - top - rows*cell.y)/2;
  return index => ({x:bench.x + (bench.w - width)/2 + (index%cell.columns)*cell.x,y:first + Math.floor(index/cell.columns)*cell.y,w:cell.w,h:cell.h});
}

/** A tile under the cut: small for a long ranking, ten to a row and large enough for its rank for a short one. */
function tile(bench: Box, top: number, rank: number, total: number, columns: number): Box {
  const index = rank - 11;
  if (total - 10 <= NUMBERED_TILES) {
    const w = (bench.w - 9*4)/10;
    return {x:bench.x + (index%10)*(w + 4),y:top + Math.floor(index/10)*26,w,h:22};
  }
  return {x:bench.x + (index%columns)*14,y:top + Math.floor(index/columns)*11,w:11,h:8};
}

/** A slot of a prompt: rows of `columns` under its header, each slot `h` tall, `rowGap` apart. */
function slotIn(area: Box, index: number, columns: number, top: number, h: number, rowGap: number): Box {
  const gap = 8, w = (area.w - gap*(columns-1))/columns;
  return {x:area.x + (index%columns)*(w+gap),y:area.y + top + Math.floor(index/columns)*(h + rowGap),w,h};
}

/** A fixed shuffle of the archive's cells, so that the needed chunks lie scattered, not in a block. */
export function scatter(count: number, seed = 7): number[] {
  const cells = Array.from({length:count},(_,index) => index);
  let state = seed;
  for (let index = count - 1; index > 0; index--) {
    state = (Math.imul(state,1664525) + 1013904223) >>> 0;
    const other = state % (index + 1);
    [cells[index],cells[other]] = [cells[other],cells[index]];
  }
  return cells;
}
