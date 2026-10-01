import {Suspense} from 'react';
import {AccountForm} from '../account-form';
export default function Page(){return <Suspense fallback={<p role="status">Loading account page…</p>}><AccountForm/></Suspense>;}
