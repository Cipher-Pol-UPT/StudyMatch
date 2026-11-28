// the clay art's gradients are found by id: each drawing prefixes them with its own, so two on a page never clash
export type U = (name: string) => string;
export const url = (id: string) => `url(#${id})`;
