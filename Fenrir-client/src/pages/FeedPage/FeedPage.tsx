import { useAdminSession } from '@/features/admin/context/AdminSessionContext'
import { useSigner } from '@/features/admin/context/SignerContext'
import FeedView from '@/features/feed/components/FeedView'

/** Admin Console entry point for the feed - always signed in (this route is gated by AppShell's
 * session guard), so it just hands FeedView the admin's own signer. See also PublicFeedPage for
 * the unauthenticated /feed route, which shares this same FeedView. */
export default function FeedPage() {
  const { session } = useAdminSession()
  const { getSigner } = useSigner()
  return <FeedView getSigner={getSigner} composerPubkeyHex={session?.pubkey} />
}
