import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Header from './components/Header';
import LoginPage from './pages/StudentPage/LoginPage';
import StudentLayout from './layouts/StudentLayout';
import StudentMain from './pages/StudentPage/StudentMain';
import AdminLayout from './layouts/AdminLayout';
import AdminMain from './pages/AdminPage/AdminMain';
import SubmissionPage from './pages/AdminPage/SubmissionPage';
import SubmissionDetailPage from './pages/AdminPage/SubmissionDetailPage';
import DeadlineManagePage from "./pages/AdminPage/DeadlineManagePage";
import RequiredFieldListPage from "./pages/AdminPage/RequiredFieldListPage";
import AddRequiredDocumentPage from "./pages/AdminPage/AddRequiredDocumentPage";

function App() {
    return (
        <BrowserRouter>
            <Header/>
            <div style={{paddingTop: '50px'}}>
                <Routes>
                    {/* 공통 라우트 */}
                    <Route path="/" element={<LoginPage/>}/>

                    {/* 학생용 레이아웃 */}
                    <Route element={<StudentLayout/>}>
                        <Route path="/student/main" element={<StudentMain/>}/>
                    </Route>

                    {/* 관리자용 레이아웃 */}
                    <Route element={<AdminLayout/>}>
                        <Route path="/admin/main" element={<AdminMain/>}/>
                        <Route path="/admin/submissions" element={<SubmissionPage/>}/>
                        <Route path="/submission/detail/:id" element={<SubmissionDetailPage/>}/>
                        <Route path="/admin/deadlines" element={<DeadlineManagePage />} />
                        <Route path="/admin/required" element={<RequiredFieldListPage />} />
                        <Route path="/admin/required/add" element={<AddRequiredDocumentPage />} />
                        <Route path="/admin/required/edit/:documentId" element={<AddRequiredDocumentPage />} />

                    </Route>
                </Routes>
            </div>
        </BrowserRouter>
    );
}

export default App;
