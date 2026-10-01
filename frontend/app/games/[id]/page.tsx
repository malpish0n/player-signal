import { ReviewsWorkspace } from "./reviews-workspace";
export default async function GamePage({ params, searchParams }: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ page?: string; language?: string; vote?: string; q?: string; from?: string; to?: string }>;
}) {
  const { id } = await params;
  const filters = await searchParams;
  return <ReviewsWorkspace key={`${id}/${filters.page}/${filters.language}/${filters.vote}/${filters.q}/${filters.from}/${filters.to}`} id={id} page={filters.page ?? "0"} language={filters.language ?? ""} vote={filters.vote ?? ""} q={filters.q ?? ""} from={filters.from ?? ""} to={filters.to ?? ""} />;
}
