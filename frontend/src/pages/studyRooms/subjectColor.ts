// the backend subjects have no colour, so pick one from a fixed palette by subject id
const PALETTE = ['#ef535b', '#6fba7b', '#51bfd7', '#df8fdf', '#9b6bd1', '#f2a65a', '#5c7cfa', '#38b2ac'];

export const subjectColor = (subjectId: number) => PALETTE[subjectId % PALETTE.length];
